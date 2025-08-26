package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.RootSnapshotBlockBuilder
import net.postchain.base.snapshot.SNAPSHOT_ROOT_EXTRA_HEADER
import net.postchain.base.withReadWriteConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.ebft.BlockWriter
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetLatestSnapshotBlock
import net.postchain.ebft.message.GetSnapshotData
import net.postchain.ebft.message.SnapshotData
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkleHash
import net.postchain.gtx.SnapshotAware
import java.lang.Thread.sleep

class SnapshotSynchronizer(
        workerContext: WorkerContext,
        private val blockDatabase: BlockWriter,
        val params: SyncParameters,
        val peerStatuses: PeerStatuses,
        val isProcessRunning: () -> Boolean,
        rateLimitConfiguration: RateLimitConfiguration,
) : AbstractSynchronizer(workerContext, rateLimitConfiguration) {

    companion object : KLogging()

    private var receivedLatestSnapshotHeight = mutableMapOf<NodeRid, BlockHeader>()
    private val waitingForSnapshotDataByContext = mutableMapOf<Long, SnapshotDataRequest>()
    private var snapshotNodes = setOf<NodeRid>()
    private lateinit var blockSnapshotRootHash: ByteArray
    private val snapshotModuleByContextMap = mutableMapOf<Long, SnapshotAware>()

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
            if (System.currentTimeMillis() - lastRequestForSnapshotHeight >= params.jobTimeout) {
                if (numberOfTries > 3) break
                val peersLeft = configuredPeers - receivedLatestSnapshotHeight.keys
                if (peersLeft.isEmpty()) break

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

            val (validator, witnessBuilder) = if (blockchainConfiguration.configHash.contentEquals(candidateHeaderConfig)) {
                val validator = blockchainConfiguration.getBlockHeaderValidator() // We have the snapshot height config now!
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            } else {
                // TODO: FETCH THE CONFIG FOR REAL FROM DB!!!
                val validator = blockchainConfiguration.getBlockHeaderValidator() // Let's cheat for now
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            }

            try {
                validator.validateWitness(BaseBlockWitness.fromBytes(candidate.witness), witnessBuilder)
                logger.info("Received snapshot info from peers, highest valid height was: ${candidateHeader.getHeight()}")
                params.syncToExactHeight  = candidateHeader.getHeight()
                candidateHeader.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]?.asByteArray()?.let {
                    blockSnapshotRootHash = it
                }
                // TODO refresh this now and then to update nodes we can retrieve snapshot data from?
                snapshotNodes = receivedLatestSnapshotHeight
                        .map { (node, header) -> if (header.header.contentEquals(candidate.header)) node else null }
                        .filterNotNull()
                        .toSet()
                return true
            } catch (e: Exception) {
                logger.warn("Received invalid snapshot header from peer: ${e.message}")
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

                    is BlockHeader -> {
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
                            verifyAndGetExpectedRequest(message)?.let {
                                waitingForSnapshotDataByContext.remove(it.contextId)

                                requestNextSnapshotData(message)
                                storeSnapshotData(message)
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

    // Snapshot responses must be validated as proofs
    private fun syncSnapshotUntil() {

        if (isProcessRunning()) {
            sendInitialSnapshotDataRequest()

            while (isProcessRunning() && waitingForSnapshotDataByContext.isNotEmpty()) {
                processMessages(false)
                processRequestTimeouts()
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

    private fun buildSnapshot(height: Long): Hash {
        return withReadWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
            val bctx = BaseBlockEContext( // TODO: keep, or change LeafStore interface?
                    ctx,
                    height,
                    -1,
                    -1,
                    mapOf()
            ) { _, _, _ -> }
            RootSnapshotBlockBuilder(bctx).build()
        }
    }

    private fun storeSnapshotData(message: SnapshotData) {
        if (message.data.isNotEmpty()) {
            withWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val module = getSnapshotAwareModuleByContext(ctx, message.contextId)

                    message.data.forEachIndexed { index, datumData ->
                        val datumId = message.datumIdFrom + index
                        val gtv = datumData.data
                        val isPermanent = datumData.isPermanent

                        logger.debug { "Got snapshot data $datumId for context ${message.contextId}" } // TODO remove

                        // TODO batch insert?
                        insertUpdatedDatum(ctx, message.contextId, DatumInfo(
                                datumId,
                                gtv.merkleHash(workerContext.blockchainConfiguration.merkleHashCalculator),
                                if (isPermanent) null else GtvEncoder.encodeGtv(gtv)
                        ))

                        // TODO batch insert?
                        module.constructDatum(ctx, datumId, gtv, isPermanent)
                    }
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

    private fun verifyAndGetExpectedRequest(message: SnapshotData): SnapshotDataRequest? {
        // TODO more verification and check proof
        val request = waitingForSnapshotDataByContext[message.contextId]
        if (request == null) {
            logger.debug { "Received snapshot data for unknown context ${message.contextId}" }
        } else if (message.datumIdFrom != request.offset) {
            logger.debug { "Received snapshot data for wrong context ${message.contextId}, expected ${request.offset}, got ${message.datumIdFrom}" }
        } else  if (message.height != params.syncToExactHeight) {
            logger.debug { "Received snapshot data for wrong height, expected ${params.syncToExactHeight}, got ${message.height}" }
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
        val requestSnapshotNodes = snapshotNodes.minus(sentTo)
        val (peer, _) = communicationManager.sendToRandomPeer(GetSnapshotData(params.syncToExactHeight, contextId, offset), requestSnapshotNodes)
        if (peer == null) {
            throw TODO("Handle this case - no more nodes?")
        }
        waitingForSnapshotDataByContext[contextId] = SnapshotDataRequest(contextId, offset,
                System.currentTimeMillis(), sentTo + setOf(peer))
        logger.debug { "Sent GetSnapshotData to peer $peer for context $contextId, offset $offset. Already sent to: $sentTo" }
    }

    private fun requestNextSnapshotData(message: SnapshotData) {
        // Request next batch unless last was empty, which means we are done
        if (message.data.isNotEmpty()) {
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

data class SnapshotDataRequest(
        val contextId: Long,
        val offset: Long,
        val timeSent: Long,
        val sentTo: Set<NodeRid>,
)