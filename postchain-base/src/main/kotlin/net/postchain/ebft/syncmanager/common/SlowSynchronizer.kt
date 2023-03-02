package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.NodeRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.BlockWitness
import net.postchain.ebft.BDBAbortException
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
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
        wrkrCntxt: WorkerContext,
        val blockDatabase: BlockDatabase,
        params: SyncParameters,
        val isProcessRunning: () -> Boolean
) : AbstractSynchronizer(wrkrCntxt) {

    private val stateMachine = SlowSyncStateMachine.buildWithChain(blockchainConfiguration.chainID.toInt())

    val peerStatuses = SlowSyncPeerStatuses(params) // Don't want to put this in [AbstractSynchronizer] b/c too much generics.

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
            stateMachine.lastCommittedBlockHeight = blockHeight

            val sleepData = SlowSyncSleepData()
            while (isProcessRunning()) {
                synchronized(stateMachine) {
                    if (stateMachine.state == SlowSyncStates.WAIT_FOR_COMMIT) {
                        // We shouldn't need to handle failed commits here, since we have the callback
                        logger.warn("Why didn't we manage to commit all blocks after the sleep? " +
                                "Expected height: ${stateMachine.lastUncommittedBlockHeight} but " +
                                "actual height: ${stateMachine.lastCommittedBlockHeight}")
                    } else {
                        processMessages(sleepData)
                        val now = System.currentTimeMillis()
                        stateMachine.maybeGetBlockRange(now, ::sendRequest) // It's up to the state machine if we should send a new request
                    }
                }
                Thread.sleep(sleepData.currentSleepMs)
            }
        } catch (e: BadDataMistake) {
            logger.error(e) { "Fatal error, shutting down blockchain for safety reasons. Needs manual investigation." }
            throw e
        } catch (e: Exception) {
            logger.debug(e) { "syncUntil() -- ${"Exception"}" }
        } finally {
            syncDebug("Await commits", blockHeight)
            peerStatuses.clear()
            syncDebug("Exit slowsync", blockHeight)
        }
    }

    private fun sendRequest(now: Long, slowSyncStateMachine: SlowSyncStateMachine, lastPeer: NodeRid? = null) {
        val startAtHeight = slowSyncStateMachine.getStartHeight()
        val excludedPeers = peerStatuses.exclNonSyncable(startAtHeight, now)
        val peers = configuredPeers.minus(excludedPeers)
        if (peers.isEmpty()) return

        // Sometimes we prefer not to use the same peer as last time
        val usePeers = if (peers.size > 1 && lastPeer != null) {
            peers.minus(lastPeer)
        } else {
            peers
        }
        val pickedPeerId = communicationManager.sendToRandomPeer(GetBlockRange(startAtHeight), usePeers).first

        if (pickedPeerId != null) {
            slowSyncStateMachine.updateToWaitForReply(pickedPeerId, startAtHeight, now)
        } else {
            logger.warn("No nodes to request blocks from. Cannot proceed. Current height: ${startAtHeight - 1}")
        }
    }

    /**
     * The only data we expect to receive is [BlockRange] from now on, we'll drop all other data packages
     * (however we will accept and handle Get-requests for blocks from other nodes)
     *
     * @return SleepData we should use to sleep
     */
    private fun processMessages(sleepData: SlowSyncSleepData) {
        for (packet in communicationManager.getPackets()) {
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
                        val processedBlocks = handleBlockRange(peerId, message.blocks, message.startAtHeight)
                        sleepData.updateData(processedBlocks)
                    }
                    is Status -> {} // Do nothing, we don't measure drained
                    else -> logger.debug { "Unhandled type $message from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }


    /**
     * This is used for syncing from old nodes that doesn't have this new Synchronizer algorithm
     *
     * @return number of processed blocks
     */
    private fun handleBlockRange(peerId: NodeRid, blocks: List<CompleteBlock>, startingAtHeight: Long): Int {

        if (stateMachine.state != SlowSyncStates.WAIT_FOR_REPLY) {
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: We are not waiting for a block range. " +
                    " Why does $peerId send us this? $stateMachine ")
            return 0
        }

        if (!stateMachine.isHeightWeWaitingFor(startingAtHeight)) {
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: Peer: ${stateMachine.waitForNodeId} is sending us a block range " +
                    "(startingAtHeight = $startingAtHeight) while we expected start at height: ${stateMachine.waitForHeight}.")
            return 0
        }

        if (!stateMachine.isPeerWeAreWaitingFor(peerId)) {
            // Perhaps this is due to our initial request timed out, we are indeed waiting for this block range, so let's use it
            logger.debug("Slow Sync: We didn't expect $peerId to send us a block range (startingAtHeight = $startingAtHeight) " +
                    "(We wanted ${stateMachine.waitForNodeId} to do it).")
        }

        logger.debug("Got ${blocks.size} from peer $peerId (starting at height $startingAtHeight).")
        var expectedHeight = startingAtHeight
        for (block in blocks) {
            val blockData = block.data
            val headerWitnessPair = handleBlockHeader(peerId, blockData.header, block.witness, expectedHeight)
                ?: return (expectedHeight - startingAtHeight).toInt() // Header failed for some reason. Just give up
            handleBlock(
                    peerId,
                    headerWitnessPair.first,
                    headerWitnessPair.second,
                    expectedHeight,
                    blockData.transactions
            )
            expectedHeight++ // We expect blocks to be in the correct order in the list
        }
        val processedBlocks = (expectedHeight - startingAtHeight).toInt()
        if (processedBlocks != blocks.size) {
            stateMachine.state = SlowSyncStates.WAIT_FOR_ACTION
            throw ProgrammerMistake("processedBlocks != blocks.size")
        }
        if (processedBlocks == 0) {
            stateMachine.state = SlowSyncStates.WAIT_FOR_ACTION
        }
        return processedBlocks
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
            Pair(h, w)
        } else {
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: Invalid header received. Height: $requestedHeight")
            null
        }
    }

    private fun handleBlock(
            peerId: NodeRid,
            header: net.postchain.core.block.BlockHeader,
            witness: BlockWitness,
            height: Long,
            txs: List<ByteArray>
    ) {
        if (header !is BaseBlockHeader) {
            throw BadDataMistake(BadDataType.BAD_MESSAGE, "Expected BaseBlockHeader")
        }

        logger.trace { "handleBlock() - Received for height: $height" }
        var bTrace: BlockTrace? = null
        if (logger.isTraceEnabled) {
            logger.trace("handleBlock() - Creating block trace with procname: $procName , height: $height")

            bTrace = BlockTrace.build(procName, header.blockRID, height)
        }

        // The witness has already been verified in handleBlockHeader().
        val block = BlockDataWithWitness(header, txs, witness)

        stateMachine.updateToWaitForCommit(height, System.currentTimeMillis())
        commitBlock(peerId, bTrace, block, height)
    }

    /**
     * NOTE:
     * If one block fails to commit, don't worry about the blocks coming after. This is handled in the BBD.addBlock().
     */
    private fun commitBlock(peerId: NodeRid, bTrace: BlockTrace?, block: BlockDataWithWitness, height: Long) {
        if (addBlockCompletionFuture?.isDone == true) {
            addBlockCompletionFuture = null // If it's done we don't need the future
        }

        // (this is usually slow and is therefore handled via a future).
        addBlockCompletionFuture = blockDatabase
                .addBlock(block, addBlockCompletionFuture, bTrace)
                .whenCompleteUnwrapped { _: Any?, exception ->
                    synchronized(stateMachine) {
                        if (exception == null) {
                            logger.debug { "commitBlock() - Block height: $height committed successfully." }
                            try {
                                stateMachine.updateAfterSuccessfulCommit(height)
                                blockHeight = height
                            } catch (t: Throwable) {
                                logger.warn(t) { "Failed to update after successful commit" }
                            }
                        } else {
                            if (exception is PmEngineIsAlreadyClosed || exception is BDBAbortException) {
                                if (logger.isTraceEnabled) {
                                    logger.warn { "Exception committing block height $height from peer: $peerId: ${exception.message}, cause: ${exception.cause}, from bTrace: ${bTrace?.toString()}" }
                                } else {
                                    logger.warn { "Exception committing block height $height from peer: $peerId: ${exception.message}, cause: ${exception.cause}" }
                                }
                            } else {
                                if (logger.isTraceEnabled) {
                                    logger.warn(exception) { "Exception committing block height $height from peer: $peerId from bTrace: ${bTrace?.toString()}" }
                                } else {
                                    logger.warn(exception) { "Exception committing block height $height from peer: $peerId" }
                                }
                            }
                            stateMachine.updateAfterFailedCommit(height)
                        }
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