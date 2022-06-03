package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.BadDataMistake
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.CompletionPromise
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.Status
import net.postchain.ebft.worker.WorkerContext

/**
 * Used by replicas only!
 * Will consume blocks in the same pace as they are made, to avoid spamming the block producers too much.
 *
 * To consume blocks fast, use [FastSynchronizer]
 *
 * Slow sync will only call one node, and try to get as many blocks as possible from that node (max 10).
 * - If we get 10 we will immediately try to get 10 more (b/c there are more to get).
 * - If we get <10 we will take a "nap" and ask again later.
 *
 * The replica shouldn't be more than a second behind the cluster, so the "nap" should be less than a second.
 */
class SlowSynchronizer(
    private val wrkrCntxt: WorkerContext,
    val blockDatabase: BlockDatabase,
    private val prms: FastSyncParameters,
    val isProcessRunning: () -> Boolean
) : AbstractSynchronizer(wrkrCntxt, prms) {

    private var lastBlockTimestamp: Long = blockQueries.getLastBlockTimestamp().get()

    // this is used to track pending asynchronous BlockDatabase.addBlock tasks to make sure failure to commit propagates properly
    private var addBlockCompletionPromise: CompletionPromise? = null

    companion object : KLogging()


    /**
     * We sync slowly. We typically wait for a node to respond before asking for more, unless it's exceptionally slow.
     *
     * We'll sync forever (only exception is if the node is going down).
     */
    fun syncUntil() {
        try {
            blockHeight = blockQueries.getBestHeight().get()
            syncDebug("Start", blockHeight)
            lastBlockTimestamp = blockQueries.getLastBlockTimestamp().get()
            while (isProcessRunning()) {
                processMessages()
                Thread.sleep(params.loopInterval)
            }
        } catch (e: BadDataMistake) {
            FastSynchronizer.logger.error(e) { "Fatal error, shutting down blockchain for safety reasons. Needs manual investigation." }
            throw e
        } catch (e: Exception) {
            FastSynchronizer.logger.debug(e) { "syncUntil() -- ${"Exception"}" }
        } finally {
            syncDebug("Await commits", blockHeight)
            peerStatuses.clear()
            syncDebug("Exit fastsync", blockHeight)
        }
    }

    private fun processMessages() {
        for (packet in communicationManager.getPackets()) {
            // We do heartbeat check for each network message because
            // communicationManager.getPackets() might give a big portion of messages.
            if (!workerContext.awaitPermissionToProcessMessages(lastBlockTimestamp) { !isProcessRunning() }) {
                return
            }

            val peerId = packet.first
            if (peerStatuses.isBlacklisted(peerId)) {
                continue
            }
            val message = packet.second
            if (message is GetBlockHeaderAndBlock || message is BlockHeader) {
                peerStatuses.confirmModern(peerId)
            }
            try {
                when (message) {
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight)
                    //in GetBlockRange -> sendBlockRange(peerId, message.height, blockHeight)
                    //is BlockHeader -> handleBlockHeader(peerId, message.header, message.witness, message.requestedHeight)
                    //is UnfinishedBlock -> handleUnfinishedBlock(peerId, message.header, message.transactions)
                    //is CompleteBlock -> handleCompleteBlock(peerId, message.data, message.height, message.witness)
                    is Status -> peerStatuses.statusReceived(peerId, message.height - 1)
                    else -> logger.trace { "Unhandled type ${message} from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }


    /*

    private fun handleBlockRange(peerId: NodeRid, msgHeight: Long, blockHeight: Long) {
        val h = blockchainConfiguration.decodeBlockHeader(header)
        if (h !is BaseBlockHeader) {
            throw BadDataMistake(BadDataType.BAD_MESSAGE, "Expected BaseBlockHeader")
        }
        val height = getHeight(h)
        val j = jobs[height]
        if (j == null) {
            peerStatuses.maybeBlacklist(peerId, "Synch: Why did we get an unfinished block of height: $height from peer: $peerId ? We didn't ask for it")
            return
        }
        unfinishedTrace("Received for $j")
        var bTrace: BlockTrace? = null
        if (logger.isDebugEnabled) {
            bTrace = BlockTrace.build(null, h.blockRID, height)
        }
        val expectedHeader = j.header

        // Validate everything!
        if (j.block != null) {
            peerStatuses.maybeBlacklist(peerId, "Synch: We got this block height = $height already, why send it again?. $j")
            return
        }

        if (peerId != j.peerId) {
            peerStatuses.maybeBlacklist(peerId, "Synch: We didn't expect $peerId to send us an unfinished block (height = $height). We wanted ${j.peerId} to do it. $j")
            return
        }

        if (expectedHeader == null) {
            peerStatuses.maybeBlacklist(peerId, "Synch: We don't have a header yet, why does $peerId send us an unfinished block (height = $height )? $j")
            return
        }

        if (!(expectedHeader.rawData contentEquals header)) {
            peerStatuses.maybeBlacklist(peerId, "Synch: Peer: ${j.peerId} is sending us an unfinished block (height = $height) with a header that doesn't match the header we expected. $j")
            return
        }

        // The witness has already been verified in handleBlockHeader().
        j.block = BlockDataWithWitness(h, txs, j.witness!!)

        commitJobsAsNecessary(bTrace)
    }

     */

    // -------------
    // Only logging below
    // -------------

    private fun syncDebug(message: String, height: Long, e: Exception? = null) {
        logger.debug(e) { "syncUntil() -- $message, at height: $height" }
    }
}