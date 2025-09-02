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
import org.apache.commons.io.FileUtils
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
    private val contextSyncState = mutableMapOf<Long, SnapshotContextState>() // State of progress per context, each context is removed on completion
    private lateinit var blockSnapshotRootHash: ByteArray
    internal val snapshotModuleByContextMap = mutableMapOf<Long, SnapshotAware>()

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
        val snapshotCandidates = receivedLatestSnapshotHeight
                .filter { (_, blockHeader) -> blockHeader.header.isNotEmpty() }
                .map { (peerId, blockHeader) -> peerId to blockHeader }
                .sortedByDescending { (_, blockHeader) -> BlockHeaderData.fromBinary(blockHeader.header).getHeight() }
        // TODO: Return false if we are below snapshot sync threshold?

        // We try all but unless peers are malicious or we are lacking config it should be fine
        for ((peerId, candidate) in snapshotCandidates) {
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
                        snapshotHeightConfigData!!.signers.toTypedArray()
                )
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            }

            try {
                validator.validateWitness(BaseBlockWitness.fromBytes(candidate.witness), witnessBuilder)
                logger.info("Received snapshot info from peers, highest valid height was: $candidateHeaderHeight")
                params.syncToExactHeight  = candidateHeaderHeight
                candidateHeader.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]?.asByteArray()?.let { blockSnapshotRootHash = it }
                blockHeaderContextData = getVerifiedContextData(candidate)
                setInitialNodeStates(candidate)
                logger.info("Snapshot sync starts from nodes: ${peerStatuses.getSyncablePeers(candidateHeaderHeight)}")
                return true
            } catch (e: Exception) {
                with("Received invalid snapshot header from peer: ${e.message}") {
                    logger.warn(this, e)
                    peerStatuses.maybeBlacklist(peerId, this)
                }
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
                            verifyAndGetExpectedRequest(message, peerId)?.let { state ->
                                if (!message.data.isNullOrEmpty()) {
                                    val preparedData = message.data.mapIndexed { index, datum ->
                                        FullSnapshotDatumData(
                                                message.datumIdFrom + index,
                                                datum.data,
                                                datum.data.merkleHash(workerContext.blockchainConfiguration.merkleHashCalculator),
                                                datum.isPermanent)
                                    }

                                    val (valid, end) = verifyDataProof(message.contextId, message.datumIdFrom, message.proof!!, preparedData)
                                    if (valid) {
                                        if (!end) {
                                            requestNextSnapshotData(state, message)
                                        }

                                        val storeTime = measureTime {
                                            storeSnapshotData(message.contextId, preparedData)
                                        }
                                        logger.debug { "Stored ${preparedData.size} datums (${FileUtils.byteCountToDisplaySize(preparedData.sumOf { it.data.nrOfBytes() })} bytes) from offset ${message.datumIdFrom} for context id ${message.contextId} in ${storeTime.toLong(DurationUnit.MILLISECONDS)} ms" }

                                        if (end) {
                                            logger.debug { "Snapshot end reached for context ${state.contextId}" }
                                            contextSyncState.remove(state.contextId)
                                        }
                                    } else {
                                        with("Snapshot data received from $peerId is not valid for context ${state.contextId} and offset ${state.offset}") {
                                            logger.warn(this)
                                            peerStatuses.maybeBlacklist(peerId, this)
                                        }
                                        sendGetSnapshotData(state)
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

    private fun verifyDataProof(contextId: Long, datumIdFrom: Long, snapshotProof: SnapshotRangeProof, data: List<FullSnapshotDatumData>): Pair<Boolean, Boolean> {
        val leafs = TreeMap<Long, Hash>()
        data.forEach {
            leafs[it.datumId] = it.hash
        }
        val proof = RangeProof(snapshotProof.leftBoundaryHashes, snapshotProof.rightBoundaryHashes, snapshotProof.commonPath)
        return verifyRangeProof.verify(blockHeaderContextData[contextId]!!.rootHash, proof, datumIdFrom, leafs.values.toList())
    }

    // Snapshot responses must be validated as proofs
    private fun syncSnapshotUntil() {

        if (isProcessRunning()) {
            sendInitialSnapshotDataRequest()

            while (isProcessRunning() && contextSyncState.isNotEmpty()) {
                processMessages(false)
                processRequestTimeouts()
                addMoreSnapshotNodes()
                sleep(params.loopInterval)
            }

            if (isProcessRunning() && contextSyncState.isEmpty()) {
                val latestSnapshotRootHash = buildSnapshot(params.syncToExactHeight)
                if (blockSnapshotRootHash.contentEquals(latestSnapshotRootHash)) {
                    logger.info("Finished syncing snapshot")
                } else {
                    logger.warn("Finished syncing snapshot, but root hashes do not match")
                    throw ProgrammerMistake("Snapshot root hashes do not match")
                }
            }
        }
    }

    private fun addMoreSnapshotNodes() {
        val drainedNodes = peerStatuses.getPeersWithStatus(KnownState.State.DRAINED)
        if (
                params.snapshotSyncNodesUpdateIntervalTime > 0 &&
                drainedNodes.isNotEmpty() &&
                System.currentTimeMillis() - lastSnapshotNodesUpdate >= params.snapshotSyncNodesUpdateIntervalTime
        ) {
            val node = drainedNodes.random()
            peerStatuses.markSyncable(node)
            logger.debug { "Node $node marked as syncable to attempt retrieve snapshot data from node" }
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

    private fun verifyAndGetExpectedRequest(message: SnapshotData, peerId: NodeRid): SnapshotContextState? {
        val contextState = contextSyncState[message.contextId]
        var error: String? = null
        if (contextState == null) {
            error = "Received snapshot data for unknown context ${message.contextId}"
        } else if (message.datumIdFrom != contextState.offset) {
            error = "Received incorrect offset in snapshot data for context ${message.contextId}, expected offset ${contextState.offset}, got ${message.datumIdFrom}"
        } else if (message.height != params.syncToExactHeight) {
            error = "Received snapshot data for wrong height, expected ${params.syncToExactHeight}, got ${message.height}"
        } else if (message.data == null) {
            logger.debug { "Node $peerId does not have requested height ${message.height} in snapshot context ${message.contextId}. Node is removed from list and request is sent to another node." }
            peerStatuses.drained(peerId, -1)
            sendGetSnapshotData(contextState)
        } else if (message.data.isNotEmpty() && message.proof == null) {
            error = "Node $peerId did not include snapshot proof for height ${message.height} in context ${message.contextId}."
        } else {
            return contextState
        }

        if (error != null) {
            logger.warn(error)
            peerStatuses.maybeBlacklist(peerId, error)
        }
        return null
    }

    private fun sendInitialSnapshotDataRequest() {
        blockQueries.getSnapshotContextMaxIds(params.syncToExactHeight).get()
                .forEach { (contextId, _) ->
                    // TODO we will for now always start from 0, but on restart etc we could continue sync from the same height
                    val state = SnapshotContextState(contextId)
                    contextSyncState[state.contextId] = state
                    sendGetSnapshotData(state)
                }
    }

    private fun sendGetSnapshotData(contextState: SnapshotContextState) {
        val message = GetSnapshotData(params.syncToExactHeight, contextState.contextId, contextState.offset)
        val snapshotNodes = peerStatuses.getSyncablePeers(params.syncToExactHeight)
        val peer = communicationManager.sendToRandomPeer(message, snapshotNodes.minus(contextState.sentTo.toSet())).first ?: let {
            logger.info { "Snapshot data request has been sent to all available nodes without any response. Retrying with random nodes." }
            communicationManager.sendToRandomPeer(message, snapshotNodes).first
        }
        if (peer == null) {
            logger.info { "Couldn't find any node to send snapshot data request to for context ${contextState.contextId} and offset ${contextState.offset}." }
        } else {
            contextState.sentTo.add(peer)
            logger.debug { "Sent GetSnapshotData to peer $peer for context ${contextState.contextId}, offset ${contextState.offset}. Already sent to: ${contextState.sentTo}" }
        }
        contextState.timeSent = System.currentTimeMillis()
    }

    private fun requestNextSnapshotData(state: SnapshotContextState, message: SnapshotData) {
        // Request next batch unless last was empty, which means we are done
        if (message.data!!.isNotEmpty()) {
            state.offset += message.data.size
            state.sentTo.clear()
            sendGetSnapshotData(state)
        }
    }

    private fun processRequestTimeouts() {
        val now = System.currentTimeMillis()
        contextSyncState
                .filterValues { it.timeSent + params.jobTimeout < now }
                .values
                .forEach {
            logger.debug { "Snapshot request timed out for context ${it.contextId} and offset ${it.offset}, sending request to another node" }
                    peerStatuses.unresponsive(it.sentTo.last(), "Snapshot request timed out")
                    sendGetSnapshotData(it)
        }
    }

    private fun getVerifiedContextData(candidate: SnapshotBlockHeader): Map<Long, SnapshotBlockHeaderContextData> {
        val rootHashOfContextHashes = verifyRangeProof.calculateMerkleRoot(
                candidate.contextData.map { it.rootHash },
                workerContext.blockchainConfiguration.snapshot.levelsPerPage)
        if (!rootHashOfContextHashes.contentEquals(blockSnapshotRootHash)) {
            throw ProgrammerMistake("Merkle root of snapshot context data does not match")
        }
        return candidate.contextData.associateBy { it.contextId }
    }

    // Set nodes with candidate heigher syncable, the rest drained or unresponsive
    private fun setInitialNodeStates(candidate: SnapshotBlockHeader) {
        configuredPeers.minus(receivedLatestSnapshotHeight.keys).forEach {
            peerStatuses.unresponsive(it, "Never received LatestSnapshotBlock")
        }
        val syncableNodes = receivedLatestSnapshotHeight.filterValues { it.header.contentEquals(candidate.header) }
        peerStatuses.getAllPeers()
                .forEach {
                    if (syncableNodes.containsKey(it)) {
                        peerStatuses.markSyncable(it)
                    } else {
                        peerStatuses.drained(it, BlockHeaderData.fromBinary(receivedLatestSnapshotHeight[it]!!.header).getHeight())
                    }
                }
        lastSnapshotNodesUpdate = System.currentTimeMillis()
    }
}

/** Track active requests */
data class SnapshotContextState(
        val contextId: Long,
        var offset: Long = 0,
        var timeSent: Long = System.currentTimeMillis(),

        /** Nodes that have already been sent current request, in order */
        val sentTo: MutableList<NodeRid> = mutableListOf(),
)

data class FullSnapshotDatumData(val datumId: Long, val data: Gtv, val hash: Hash, val isPermanent: Boolean)