package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.NodeRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.BlockWitness
import net.postchain.ebft.BDBAbortException
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.*
import net.postchain.ebft.worker.WorkerContext
import net.postchain.ebft.syncmanager.common.SlowSyncSleepConst.MAX_PEER_WAIT_TIME_MS

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

    // Slow Sync special stuff
    private var waitForPeerId: NodeRid? = null // Are we waiting for a block range?
    private var waitForHeight: Long? = null // The height we are waiting for
    private var waitTime: Long? = null // When did we last ask for a BlockRange
    private var lastUncommittedBlockHeight = 0L
    @Volatile
    private var lastCommittedBlockHeight = 0L // Only update this after the actual commit

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
            var sleepData = SlowSyncSleepData()

            while (isProcessRunning()) {
                if (lastUncommittedBlockHeight == lastCommittedBlockHeight) {
                    processMessages(sleepData)
                    maybeSendGetBlockRange()
                } else {
                    logger.warn("Why didn't we manage to commit all blocks after the sleep? Expected " +
                            "height: $lastUncommittedBlockHeight but actual height: $lastCommittedBlockHeight")
                    // We have to assume
                }
                Thread.sleep(sleepData.currentSleepMs)
            }
        } catch (e: BadDataMistake) {
            FastSynchronizer.logger.error(e) { "Fatal error, shutting down blockchain for safety reasons. Needs manual investigation." }
            throw e
        } catch (e: Exception) {
            FastSynchronizer.logger.debug(e) { "syncUntil() -- ${"Exception"}" }
        } finally {
            syncDebug("Await commits", blockHeight)
            peerStatuses.clear()
            syncDebug("Exit slowsync", blockHeight)
        }
    }

    /**
     * If we are not waiting for a BlockRange we should ask someone for it.
     */
    private fun maybeSendGetBlockRange() {
        val now = System.currentTimeMillis()
        if (waitForHeight == null) {
            val startingAtHeight =  lastCommittedBlockHeight + 1
            sendRequest(startingAtHeight, null) // We don't mind asking the old peer
        } else if (waitTime != null &&
            now > (waitTime!! + MAX_PEER_WAIT_TIME_MS)) {
            // We waited too long, let's ask someone else
            sendRequest(waitForHeight!!, waitForPeerId)
        } else {
            // Still waiting for last request, go back to sleep
        }
    }

    private fun sendRequest(startAtHeight: Long, lastPeer: NodeRid? = null) {
        val now = System.currentTimeMillis()
        val excludedPeers = peerStatuses.exclNonSyncable(startAtHeight, now)
        val peers = configuredPeers.minus(excludedPeers)
        if (peers.isEmpty()) return

        // Sometimes we prefer not to use the same peer as last time
        val usePeers = if (peers.size > 1 && lastPeer != null) {
            peers.minus(lastPeer)
        } else {
            peers
        }
        val pickedPeerId = communicationManager.sendToRandomPeer(GetBlockRange(startAtHeight), usePeers)

        if (pickedPeerId != null) {
            this.waitTime = now
            this.waitForHeight = startAtHeight
            this.waitForPeerId = pickedPeerId
        } else {
            logger.warn("No nodes to request blocks from. Cannot proceed. Current height: ${startAtHeight - 1}")
        }
    }

    /**
     * The only data we expect to receive is [BlockRange] from now on, we'll drop all other data packages
     * (however we will accept and handle Get-requests for blocks from other nodes)
     *
     * @param oldSleepData is the sleep information from the previous sleep cycle
     * @return SleepData we should use to sleep
     */
    private fun processMessages(sleepData: SlowSyncSleepData) {

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
                    // We will answer any get call
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight)
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight) // A replica might ask us

                    // But we only expect ranges and status to be sent to us
                    is BlockRange -> {
                        val processedBlocks = handleBlockRange(peerId, message.blocks, message.startAtHeight).toInt()
                        sleepData.updateData(processedBlocks)
                    }
                    is Status -> peerStatuses.statusReceived(peerId, message.height - 1)
                    else -> logger.debug { "Unhandled type ${message} from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }


    /**
     * This is used for syncing from old nodes that doesn't have this new FastSynchronizer algorithm
     *
     * @return number of processed blocks
     */
    private fun handleBlockRange(peerId: NodeRid, blocks: List<CompleteBlock>, startingAtHeight: Long): Long {

        if (waitForHeight == null) {
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: We are not waiting for a block range, why does $peerId send us this? (startingAtHeight = $startingAtHeight )? ")
            return 0
        }

        if (waitForHeight != startingAtHeight) {
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: Peer: ${waitForPeerId} is sending us a block range (startingAtHeight = $startingAtHeight) while we expected start at height: $waitForHeight")
            return 0
        }

        if (peerId != waitForPeerId) {
            // Perhaps this is due to our initial request timed out, we are indeed waiting for this block range, so let's use it
            logger.debug("Slow Synch: We didn't expect $peerId to send us a block range (startingAtHeight = $startingAtHeight) " +
                    "(We wanted $waitForPeerId to do it).")
        }

        logger.info("Got ${blocks.size} from peer $peerId (starting at height $startingAtHeight).")
        var expectedHeight = startingAtHeight
        for (block in blocks) {
            val blockData = block.data
            val headerWitnessPair = handleBlockHeader(peerId, blockData.header, block.witness, expectedHeight)
                ?: return expectedHeight - startingAtHeight // Header failed for some reason. Just give up
            handleUnfinishedBlock(peerId, headerWitnessPair.first, headerWitnessPair.second, expectedHeight, blockData.transactions)
            expectedHeight++ // We expect blocks to be in the correct order in the list
        }
        return expectedHeight - startingAtHeight // Processed blocks
    }

    /**
     * @return true if we could extract the header and it was considered valid.
     */
    private fun handleBlockHeader(
        peerId: NodeRid,
        header: ByteArray,
        witness: ByteArray,
        requestedHeight: Long
    ): Pair<net.postchain.core.block.BlockHeader, BlockWitness>? {

        if (header.isEmpty()) {
            if (witness.isEmpty()) {
                // Shouldn't happen if peer was working
                peerStatuses.maybeBlacklist(peerId, "Slow Sync: Sent empty header at height: $requestedHeight ")

                val now = System.currentTimeMillis()
                peerStatuses.drained(peerId, -1, now)
            } else {
                peerStatuses.maybeBlacklist(peerId, "Slow Sync: Why we get a witness without a header? Height: $requestedHeight ")
            }
            return null
        }

        val h = blockchainConfiguration.decodeBlockHeader(header)
        val peerBestHeight = getHeight(h)

        if (peerBestHeight != requestedHeight) {
            // Could be a bug
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: Header height=$peerBestHeight, we espected height: $requestedHeight.")
            return null
        }

        val w = blockchainConfiguration.decodeWitness(witness)
        val validator = blockchainConfiguration.getBlockHeaderValidator()
        val witnessBuilder = validator.createWitnessBuilderWithoutOwnSignature(h)

        return if (validator.validateWitness(w, witnessBuilder)) {
            logger.trace { "handleBlockHeader() -- Header for height $requestedHeight received" }
            peerStatuses.headerReceived(peerId, peerBestHeight)
            Pair(h, w)
        } else {
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: Invalid header received. Height: $requestedHeight")
            null
        }
    }

    private fun handleUnfinishedBlock(
        peerId: NodeRid,
        header: net.postchain.core.block.BlockHeader,
        witness: BlockWitness,
        height: Long,
        txs: List<ByteArray>
    ) {
        if (header !is BaseBlockHeader) {
            throw BadDataMistake(BadDataType.BAD_MESSAGE, "Expected BaseBlockHeader")
        }

        unfinishedTrace("Received for height: $height")
        var bTrace: BlockTrace? = null
        if (logger.isDebugEnabled) {
            bTrace = BlockTrace.build(null, header.blockRID, height)
        }

        // The witness has already been verified in handleBlockHeader().
        val block = BlockDataWithWitness(header, txs, witness)

        commitBlock(peerId, bTrace, block, height)
    }

    /**
     * NOTE:
     * If one block fails to commit, don't worry about the blocks coming after. This is handled in the BBD.addBlock().
     */
    private fun commitBlock(peerId: NodeRid, bTrace: BlockTrace?, block: BlockDataWithWitness, height: Long) {
        if (addBlockCompletionPromise?.isDone() == true) {
            addBlockCompletionPromise = null // If it's done we don't need the promise
        }
        lastUncommittedBlockHeight = height

        // (this is usually slow and is therefore handled via a promise).
        addBlockCompletionPromise = blockDatabase
            .addBlock(block, addBlockCompletionPromise, bTrace)
            .success {
                lastCommittedBlockHeight = height
            }
            .fail {
                // peer and try another peer
                if (it is PmEngineIsAlreadyClosed || it is BDBAbortException) {
                    logger.warn { "Exception committing block height $height from peer $peerId: ${it.message}" }
                } else {
                    logger.warn(it) { "Exception committing block height $height from peer $peerId:" }
                }
            }
    }

    // -------------
    // Only logging below
    // -------------

    private fun syncDebug(message: String, height: Long, e: Exception? = null) {
        logger.debug(e) { "syncUntil() -- $message, at height: $height" }
    }
}