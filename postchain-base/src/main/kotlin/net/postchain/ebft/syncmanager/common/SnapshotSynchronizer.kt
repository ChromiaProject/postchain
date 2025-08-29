package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseBlockWitness
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.snapshot
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.RangeProof
import net.postchain.base.snapshot.RootSnapshotBlockBuilder
import net.postchain.base.snapshot.SNAPSHOT_ROOT_EXTRA_HEADER
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.VerifyRangeProof
import net.postchain.base.withReadConnection
import net.postchain.base.withReadWriteConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.ebft.BlockWriter
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetLatestSnapshotBlock
import net.postchain.ebft.message.GetSnapshotData
import net.postchain.ebft.message.SnapshotBlockHeader
import net.postchain.ebft.message.SnapshotBlockHeaderContextData
import net.postchain.ebft.message.SnapshotData
import net.postchain.ebft.message.SnapshotRangeProof
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkleHash
import net.postchain.gtx.SnapshotAware
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import java.lang.Thread.sleep
import java.util.TreeMap
import kotlin.time.DurationUnit
import kotlin.time.measureTime

class SnapshotSynchronizer(
        workerContext: WorkerContext,
        private val blockDatabase: BlockWriter,
        val params: SyncParameters,
        val peerStatuses: PeerStatuses,
        val isProcessRunning: () -> Boolean,
        rateLimitConfiguration: RateLimitConfiguration,
        private var verifyRangeProof: VerifyRangeProof = VerifyRangeProof(SimpleDigestSystem(workerContext.appConfig.cryptoSystem))
) : AbstractSynchronizer(workerContext, rateLimitConfiguration) {

    companion object : KLogging() {
        const val SNAPSHOT_CONFIG_FETCH_RETRY_INTERVAL = 10_000L
    }

    private var receivedLatestSnapshotHeight = mutableMapOf<NodeRid, SnapshotBlockHeader>()
    private val waitingForSnapshotDataByContext = mutableMapOf<Long, SnapshotDataRequest>()
    private lateinit var blockSnapshotRootHash: ByteArray
    internal val snapshotModuleByContextMap = mutableMapOf<Long, SnapshotAware>()

    private var snapshotNodes = mutableSetOf<NodeRid>()
    private var noneSnapshotNodes = mutableListOf<NodeRid>()
    private var lastSnapshotNodesUpdate = Long.MAX_VALUE

    var blockHeaderContextData: Map<Long, SnapshotBlockHeaderContextData> = emptyMap()
        private set

    // TODO: Fetch snapshot info and determine if it is worth it
    fun trySnapshotSync() {
        if (shouldDoSnapshotSync()) {
            // Start with blocks, we have a blockdb that simply saves the block without applying txs (after checking signature)
            // TODO: Probably good with some extra parallelism here?
            val fastSynchronizer = FastSynchronizer(
                    workerContext,
                    blockDatabase,
                    params,
                    PeerStatuses(params),
                    { isProcessRunning() },
                    rateLimitConfiguration
            )
            fastSynchronizer.syncUntil { !isProcessRunning() }
            syncSnapshotUntil()
            params.syncToExactHeight = -1
        }
    }

    private fun shouldDoSnapshotSync(): Boolean {
        var lastRequestForSnapshotHeight = 0L
        var numberOfTries = 0
        while (isProcessRunning()) {
            val peersLeft = configuredPeers - receivedLatestSnapshotHeight.keys
            if (peersLeft.isEmpty()) break

            if (System.currentTimeMillis() - lastRequestForSnapshotHeight >= params.jobTimeout) {
                if (numberOfTries > 3) break

                peersLeft.forEach {
                    communicationManager.sendPacket(GetLatestSnapshotBlock(), it)
                    logger.info("Requested latest snapshot block from peer $it")
                }
                lastRequestForSnapshotHeight = System.currentTimeMillis()
                numberOfTries++
            }
            processMessages(true)
            sleep(params.loopInterval)
        }
        // We need to validate the witness of these headers
        val snapshotCandidates = receivedLatestSnapshotHeight.values.filter { it.header.isNotEmpty() }
                .sortedByDescending { BlockHeaderData.fromBinary(it.header).getHeight() }
        // TODO: Return false if we are below snapshot sync threshold?

        // We try all but unless peers are malicious or we are lacking config it should be fine
        for (candidate in snapshotCandidates) {
            val candidateHeader = BlockHeaderData.fromBinary(candidate.header)
            val candidateHeaderConfig = candidateHeader.getExtra()[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()
            val candidateHeaderRid = BlockRid(candidateHeader.toGtv().merkleHash(blockchainConfiguration.merkleHashCalculator))
            val candidateHeaderHeight = candidateHeader.getHeight()

            val (validator, witnessBuilder) = if (blockchainConfiguration.configHash.contentEquals(candidateHeaderConfig)) {
                val validator = blockchainConfiguration.getBlockHeaderValidator() // We have the snapshot height config now!
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            } else {
                val bcConfigProvider = workerContext.blockchainConfigurationProvider

                var snapshotHeightConfigData: BlockchainConfigurationData? = null
                while (snapshotHeightConfigData == null) {
                    withReadConnection(workerContext.engine.blockBuilderStorage, blockchainConfiguration.chainID) { ctx ->
                        bcConfigProvider.getHistoricConfiguration(ctx, blockchainConfiguration.chainID, candidateHeaderHeight)
                    }?.let {
                        val snapshotAppliedConfigData = BlockchainConfigurationData.fromRaw(it)
                        if (candidateHeaderConfig != null && !snapshotAppliedConfigData.configHash.contentEquals(candidateHeaderConfig)) {
                            // There is a possibility that the snapshot header configuration is pending
                            if (bcConfigProvider is ManagedBlockchainConfigurationProvider) {
                                val matchingPendingConfig = getConfigIfPending(bcConfigProvider, candidateHeaderHeight, candidateHeaderConfig)
                                if (matchingPendingConfig != null) {
                                    snapshotHeightConfigData = BlockchainConfigurationData.fromRaw(matchingPendingConfig.fullConfig)
                                }
                            }
                        } else snapshotHeightConfigData = snapshotAppliedConfigData
                    }

                    if (snapshotHeightConfigData == null) {
                        logger.warn("Unable to find config with hash ${candidateHeaderConfig?.toHex()} at height $candidateHeaderHeight. Retrying in $SNAPSHOT_CONFIG_FETCH_RETRY_INTERVAL ms...")
                        val endTime = System.currentTimeMillis() + SNAPSHOT_CONFIG_FETCH_RETRY_INTERVAL
                        while (System.currentTimeMillis() < endTime) {
                            sleep(100)
                            if (!isProcessRunning()) return false

                            processMessages(false) // Ensure we continue to process messages
                        }
                    }
                }

                val myKeyPair = KeyPair(PubKey(workerContext.appConfig.pubKeyByteArray), PrivKey(workerContext.appConfig.privKeyByteArray))
                val validator = baseBlockWitnessProviderProvider(
                        workerContext.appConfig.cryptoSystem,
                        workerContext.appConfig.cryptoSystem.buildSigMaker(myKeyPair),
                        snapshotHeightConfigData.signers.toTypedArray()
                )
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            }

            try {
                validator.validateWitness(BaseBlockWitness.fromBytes(candidate.witness), witnessBuilder)
                logger.info("Received snapshot info from peers, highest valid height was: $candidateHeaderHeight")
                params.syncToExactHeight  = candidateHeaderHeight
                candidateHeader.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]?.asByteArray()?.let {
                    blockSnapshotRootHash = it
                }
                val rootHashOfContextHashes = verifyRangeProof.calculateMerkleRoot(
                        candidate.contextData.map { it.rootHash },
                        workerContext.blockchainConfiguration.snapshot.levelsPerPage)
                if (!rootHashOfContextHashes.contentEquals(blockSnapshotRootHash)) {
                    throw ProgrammerMistake("Merkle root of snapshot context data does not match")
                }
                blockHeaderContextData = candidate.contextData.associateBy { it.contextId }
                snapshotNodes = receivedLatestSnapshotHeight
                        .filterValues { it.header.contentEquals(candidate.header) }
                        .keys
                        .toMutableSet()
                noneSnapshotNodes = (configuredPeers - snapshotNodes).toMutableList()
                lastSnapshotNodesUpdate = System.currentTimeMillis()
                logger.info("Snapshot sync starts from nodes: $snapshotNodes")
                return true
            } catch (e: Exception) {
                logger.warn("Received invalid snapshot header from peer: ${e.message}", e)
            }
        }

        return false // TODO: What do we do here?
    }

    /**
     *  @param awaitsSnapshotHeights if true, expect snapshot height messages. If false, expect snapshot data messages.
     */
    private fun processMessages(awaitsSnapshotHeights: Boolean) {
        resetServedRequests()
        for ((peerId, _, message) in communicationManager.getPackets()) {
            if (peerStatuses.isBlacklisted(peerId)) {
                continue
            }
            if (message is GetBlockHeaderAndBlock || message is BlockHeader) {
                peerStatuses.confirmModern(peerId)
            }
            try {
                when (message) {
                    // We will answer any get call
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight.get())
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight.get())
                    is GetBlockSignature -> sendBlockSignature(peerId, message.blockRID)

                    is SnapshotBlockHeader -> {
                        if (awaitsSnapshotHeights) {
                            receivedLatestSnapshotHeight[peerId] = message
                            if (message.header.isNotEmpty()) {
                                logger.info("GOT LATEST SNAPSHOT HEADER: ${BlockHeaderData.fromBinary(message.header)}")
                            } else {
                                logger.info("GOT LATEST SNAPSHOT HEADER: EMPTY")
                            }
                        }
                    }
                    is SnapshotData -> {
                        if (!awaitsSnapshotHeights) {
                            verifyAndGetExpectedRequest(message, peerId)?.let {
                                waitingForSnapshotDataByContext.remove(it.contextId)

                                if (!message.data.isNullOrEmpty()) {
                                    val preparedData = message.data.mapIndexed { index, datum ->
                                        FullSnapshotDatumData(
                                                message.datumIdFrom + index,
                                                datum.data,
                                                datum.data.merkleHash(workerContext.blockchainConfiguration.merkleHashCalculator),
                                                datum.isPermanent)
                                    }

                                    if (verifyDataProof(message.contextId, message.datumIdFrom, message.proof!!, preparedData)) {
                                        requestNextSnapshotData(message)
                                        val processTime = measureTime {
                                            storeSnapshotData(message.contextId, preparedData)
                                        }
                                        logger.debug { "Stored ${preparedData.size} datums from offset ${message.datumIdFrom} for context id ${message.contextId} in ${processTime.toLong(DurationUnit.MILLISECONDS)} ms" }
                                    } else {
                                        logger.warn { "Snapshot data received from $peerId is not valid for context ${it.contextId} and offset ${it.offset}" }
                                        // TODO: Did we just loose the trust on this node? :thinking:
                                        sendGetSnapshotData(it.contextId, it.offset, it.sentTo + peerId)
                                    }
                                }
                            }
                        }
                    }

                    is EbftVersion -> logger.debug { "Received EbftVersion from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }

    private fun verifyDataProof(contextId: Long, datumIdFrom: Long, snapshotProof: SnapshotRangeProof, data: List<FullSnapshotDatumData>): Boolean {
        val leafs = TreeMap<Long, Hash>()
        data.forEach {
            leafs[it.datumId] = it.hash
        }

        val proof = with (snapshotProof) {
            RangeProof(leftBoundaryHashes, rightBoundaryHashes, commonPath)
        }
        return verifyRangeProof.verify(blockHeaderContextData[contextId]!!.rootHash, proof, datumIdFrom, leafs.values.toList())
    }

    // Snapshot responses must be validated as proofs
    private fun syncSnapshotUntil() {

        if (isProcessRunning()) {
            sendInitialSnapshotDataRequest()

            while (isProcessRunning() && waitingForSnapshotDataByContext.isNotEmpty()) {
                processMessages(false)
                processRequestTimeouts()
                addMoreSnapshotNodes()
                sleep(params.loopInterval)
            }

            val latestSnapshotRootHash = buildSnapshot(params.syncToExactHeight)
            if (blockSnapshotRootHash.contentEquals(latestSnapshotRootHash)) {
                logger.info("Finished syncing snapshot")
            } else {
                logger.warn("Finished syncing snapshot, but root hashes do not match")
                throw ProgrammerMistake("Snapshot root hashes do not match")
            }
        }
    }

    private fun addMoreSnapshotNodes() {
        if (
                params.snapshotSyncNodesUpdateIntervalTime > 0 &&
                noneSnapshotNodes.isNotEmpty() &&
                System.currentTimeMillis() - lastSnapshotNodesUpdate >= params.snapshotSyncNodesUpdateIntervalTime
        ) {
            val node = noneSnapshotNodes.removeFirst()
            logger.debug { "Node $node added to snapshot node list: $noneSnapshotNodes" }
            snapshotNodes.add(node)
            lastSnapshotNodesUpdate = System.currentTimeMillis()
        }
    }

    private fun buildSnapshot(height: Long): Hash {
        return withReadWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
            // TODO: keep, or change LeafStore interface?
            val bctx = BaseBlockEContext(ctx, height, -1, -1, mapOf()) { _, _, _ -> }
            RootSnapshotBlockBuilder(bctx, workerContext.blockchainConfiguration.snapshot.levelsPerPage,
                workerContext.appConfig.cryptoSystem).build()
        }
    }

    private fun storeSnapshotData(contextId: Long, data: List<FullSnapshotDatumData>) {
        if (data.isNotEmpty()) {
            withWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val module = getSnapshotAwareModuleByContext(ctx, contextId)

                    val datumInfoList = data.map {
                        DatumInfo(it.datumId, it.hash, if (it.isPermanent) null else GtvEncoder.encodeGtv(it.data))
                    }
                    val datumList = data.map {
                        SnapshotDatum(it.datumId, it.data, it.isPermanent)
                    }
                    insertUpdatedDatum(ctx, contextId, datumInfoList)
                    module.constructDatum(ctx, datumList)
                }

                true
            }
        }
    }

    private fun DatabaseAccess.getSnapshotAwareModuleByContext(ctx: EContext, contextId: Long): SnapshotAware {
        return snapshotModuleByContextMap[contextId] ?: let {
            val snapshotModules = blockchainConfiguration.getSnapshotAwareModules()
            val moduleName = getSnapshotContextModule(ctx, contextId)

            val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                    ?: throw ProgrammerMistake("No module found for snapshot context id $contextId")
            snapshotModuleByContextMap[contextId] = module
            module
        }
    }

    private fun verifyAndGetExpectedRequest(message: SnapshotData, peerId: NodeRid): SnapshotDataRequest? {
        // TODO more verification and check proof
        val request = waitingForSnapshotDataByContext[message.contextId]
        if (request == null) {
            logger.debug { "Received snapshot data for unknown context ${message.contextId}" }
        } else if (message.datumIdFrom != request.offset) {
            logger.debug { "Received snapshot data for wrong context ${message.contextId}, expected ${request.offset}, got ${message.datumIdFrom}" }
        } else if (message.height != params.syncToExactHeight) {
            logger.debug { "Received snapshot data for wrong height, expected ${params.syncToExactHeight}, got ${message.height}" }
        } else if (message.data == null) {
            logger.debug { "Node $peerId does not have requested height ${message.height} in snapshot context ${message.contextId}. Node is removed from list and request is sent to another node." }
            snapshotNodes.remove(peerId)
            noneSnapshotNodes.add(peerId)
            sendGetSnapshotData(message.contextId, message.datumIdFrom)
        } else if (message.data.isNotEmpty() && message.proof == null) {
            logger.debug { "Node $peerId did not include snapshot proof for height ${message.height} in context ${message.contextId}." }
        } else {
            return request
        }
        return null
    }

    private fun sendInitialSnapshotDataRequest() {
        blockQueries.getSnapshotContextMaxIds(params.syncToExactHeight).get()
                .forEach { (contextId, maxId) ->
            sendGetSnapshotData(contextId, if (maxId == null) 0 else (maxId + 1))
        }
    }

    private fun sendGetSnapshotData(contextId: Long, offset: Long, sentTo: Set<NodeRid> = setOf()) {
        val message = GetSnapshotData(params.syncToExactHeight, contextId, offset)
        val noRequestedNodes = snapshotNodes.minus(sentTo)
        val peer = communicationManager.sendToRandomPeer(message, noRequestedNodes).first ?: let {
            logger.info { "Snapshot data request has been sent to all available nodes without any response. Retrying with random nodes." }
            communicationManager.sendToRandomPeer(message, snapshotNodes).first
        }
        if (peer == null) {
            throw ProgrammerMistake("Couldn't find any nodes to send snapshot data request to")
        }
        waitingForSnapshotDataByContext[contextId] = SnapshotDataRequest(contextId, offset,
                System.currentTimeMillis(), sentTo + setOf(peer))
        logger.debug { "Sent GetSnapshotData to peer $peer for context $contextId, offset $offset. Already sent to: $sentTo" }
    }

    private fun requestNextSnapshotData(message: SnapshotData) {
        // Request next batch unless last was empty, which means we are done
        if (message.data!!.isNotEmpty()) {
            val offset = message.datumIdFrom + message.data.size
            sendGetSnapshotData(message.contextId, offset)
        }
    }

    private fun processRequestTimeouts() {
        val now = System.currentTimeMillis()
        val requestTimeouts = waitingForSnapshotDataByContext
                .filterValues { it.timeSent + params.jobTimeout < now }
        waitingForSnapshotDataByContext.keys.removeAll(requestTimeouts.keys)
        requestTimeouts.forEach { (contextId, request) ->
            logger.debug { "Snapshot request timed out for context $contextId, sending request to another node" }
            sendGetSnapshotData(contextId, request.offset, request.sentTo)
        }
    }
}

/** Track active requests */
data class SnapshotDataRequest(
        val contextId: Long,
        val offset: Long,
        val timeSent: Long,
        val sentTo: Set<NodeRid>,
)

data class FullSnapshotDatumData(val datumId: Long, val data: Gtv, val hash: Hash, val isPermanent: Boolean)