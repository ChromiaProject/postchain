package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withWriteConnection
import net.postchain.concurrent.util.get
import net.postchain.core.BlockRid
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
            processMessages()
            Thread.sleep(params.loopInterval)
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

    private fun processMessages() {
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
                        receivedLatestSnapshotHeight[peerId] = message
                        if (message.header.isNotEmpty()) {
                            logger.info("GOT LATEST SNAPSHOT HEADER: ${BlockHeaderData.fromBinary(message.header)}")
                        } else {
                            logger.info("GOT LATEST SNAPSHOT HEADER: EMPTY")
                        }
                    }
                    // But we only expect snapshot states
                    // TODO: Handle snapshots

                    is EbftVersion -> logger.debug { "Received EbftVersion from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }

    // Snapshot responses must be validated as proofs
    fun syncSnapshotUntil() {

        if (isProcessRunning()) {
            sendInitialSnapshotDataRequest()

            while (isProcessRunning() && waitingForSnapshotDataByContext.isNotEmpty()) {
                processSnapshotMessages()
                processRequestTimeouts()
                sleep(params.loopInterval)
            }
        }
    }

    // TODO: Merge with processMessage() above?
    private fun processSnapshotMessages() {
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
                    is SnapshotData -> {
                        verifyAndGetExpectedRequest(message)?.let {
                            waitingForSnapshotDataByContext.remove(it.contextId)

                            requestNextSnapshotData(message)
                            storeSnapshotData(message)
                        }
                    }

                    is EbftVersion -> logger.debug { "Received EbftVersion from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }

    private fun storeSnapshotData(message: SnapshotData) {
        if (message.data.isNotEmpty()) {
            withWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    // TODO: Cache or preload this lookup?
                    val snapshotModules = blockchainConfiguration.getSnapshotAwareModules()
                    val moduleName = getSnapshotContextModule(ctx, message.contextId)

                    val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                            ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

                    message.data.forEachIndexed { index, datumData ->
                        val datumId = message.datumIdFrom + index
                        val gtv = datumData.first
                        val isPermanent = datumData.second

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
//        communicationManager.broadcastPacket(GetSnapshotData(params.syncToExactHeight, contextId, offset))
        val (peer, _) = communicationManager.sendToRandomPeer(GetSnapshotData(params.syncToExactHeight, contextId, offset), requestSnapshotNodes)
        if (peer == null) {
            throw TODO("Handle this case - no more nodes?")
        }
        waitingForSnapshotDataByContext[contextId] = SnapshotDataRequest(contextId, offset,
//                System.currentTimeMillis(), sentTo + setOf())
                System.currentTimeMillis(), sentTo + setOf(peer))
        logger.debug { "Sent GetSnapshotData to peer $peer for context $contextId, offset $offset. Already sent to: $sentTo" }
    }

    private fun requestNextSnapshotData(message: SnapshotData) {
        // TODO: Is it enough to identify the end by an empty response? Or do we need to match it against each context real max ids?
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