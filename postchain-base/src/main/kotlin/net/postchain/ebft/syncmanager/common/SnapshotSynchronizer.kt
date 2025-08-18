package net.postchain.ebft.syncmanager.common

import net.postchain.ebft.BlockWriter
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetLatestSnapshotBlock
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext

class SnapshotSynchronizer(
        workerContext: WorkerContext,
        private val blockDatabase: BlockWriter,
        val params: SyncParameters,
        val peerStatuses: PeerStatuses,
        val isProcessRunning: () -> Boolean,
        rateLimitConfiguration: RateLimitConfiguration,
) : AbstractSynchronizer(workerContext, rateLimitConfiguration) {

    private var waitingForPeerSnapshotInfo = true
    private var snapshotHeight = -1L

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
            params.syncToExactHeight = -1
        }
    }

    private fun shouldDoSnapshotSync(): Boolean {
        // TODO: Ask all peers if they have a snapshot and at what height
        communicationManager.broadcastPacket(GetLatestSnapshotBlock())
        // TODO: Uncomment when implemented properly
//        while (waitingForPeerSnapshotInfo) {
//            processMessages()
//            Thread.sleep(params.loopInterval)
//        }
        params.syncToExactHeight = snapshotHeight
        return true
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
                    is GetLatestSnapshotBlock -> {
                        // TODO: How do we check this?
                    }

                    is BlockHeader -> {
                        // TODO: Collect all headers and when done, validate witnesses with appropriate config for height
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

    // Maybe we can fetch blocks with existing GetBlockRange?

}