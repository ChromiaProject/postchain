package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.base.extension.getConfigHash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.BadDataException
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.BlockWitness
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.worker.WorkerContext
import java.time.Clock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

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
        workerContext: WorkerContext,
        private val blockDatabase: BlockDatabase,
        val params: SyncParameters,
        val clock: Clock,
        private val isProcessRunning: () -> Boolean,
        slowSyncStateMachineProvider: (Int) -> SlowSyncStateMachine = { chainId -> SlowSyncStateMachine.buildWithChain(chainId, params) },
        slowSyncPeerStatusesProvider: () -> SlowSyncPeerStatuses = { SlowSyncPeerStatuses(params) },
        val slowSyncSleepDataProvider: () -> SlowSyncSleepData = { SlowSyncSleepData(params) },
        reentrantLockProvider: () -> ReentrantLock = { ReentrantLock() }
) : AbstractSynchronizer(workerContext) {

    private val stateMachine = slowSyncStateMachineProvider(blockchainConfiguration.chainID.toInt())

    private val stateMachineLock = reentrantLockProvider()
    private val allBlocksCommitted = stateMachineLock.newCondition()

    val peerStatuses = slowSyncPeerStatusesProvider() // Don't want to put this in [AbstractSynchronizer] b/c too much generics.

    companion object : KLogging()

    /**
     * We sync slowly. We typically wait for a node to respond before asking for more, unless it's exceptionally slow.
     *
     * We'll sync forever (only exception is if the node is going down).
     */
    fun syncUntil() {
        try {
            val currentBlockHeight = blockQueries.getLastBlockHeight().get()
            blockHeight.set(currentBlockHeight)
            logger.debug { syncDebug("Start", currentBlockHeight) }
            stateMachine.lastCommittedBlockHeight = currentBlockHeight
            stateMachine.lastUncommittedBlockHeight = currentBlockHeight
            val sleepData = slowSyncSleepDataProvider()
            while (isProcessRunning()) {
                processMessages(sleepData)
                stateMachineLock.withLock {
                    stateMachine.maybeGetBlockRange(currentTimeMillis(), sleepData.currentSleepMs, ::sendRequest) // It's up to the state machine if we should send a new request
                }
                Thread.sleep(min(sleepData.currentSleepMs, 100))
            }
        } catch (e: BadDataException) {
            logger.error(e) { "Fatal error, shutting down blockchain for safety reasons. Needs manual investigation." }
            throw e
        } catch (e: Exception) {
            logger.debug(e) { "syncUntil() -- ${"Exception"}" }
        } finally {
            logger.debug { syncDebug("Await commits", blockHeight.get()) }
            peerStatuses.clear()
            logger.debug { syncDebug("Exit slowsync", blockHeight.get()) }
        }
    }

    internal fun sendRequest(now: Long, slowSyncStateMachine: SlowSyncStateMachine, lastPeer: NodeRid? = null) {
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
            if (stateMachine.hasUnacknowledgedFailedCommit()) stateMachine.acknowledgeFailedCommit()
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
    internal fun processMessages(sleepData: SlowSyncSleepData) {
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
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight.get())
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight.get()) // A replica might ask us
                    is GetBlockSignature -> sendBlockSignature(peerId, message.blockRID)

                    // But we only expect ranges and status to be sent to us
                    is BlockRange -> {
                        val processedBlocks = handleBlockRange(peerId, message.blocks, message.startAtHeight)
                        sleepData.updateData(processedBlocks)
                    }

                    is AppliedConfig -> {
                        if (checkIfWeNeedToApplyPendingConfig(peerId, message)) return
                    }

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
    internal fun handleBlockRange(peerId: NodeRid, blocks: List<CompleteBlock>, startingAtHeight: Long): Int {
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
            logger.debug {
                "Slow Sync: We didn't expect $peerId to send us a block range (startingAtHeight = $startingAtHeight) " +
                        "(We wanted ${stateMachine.waitForNodeId} to do it)."
            }
        }


        stateMachineLock.withLock {
            if (stateMachine.isWaitingForBlocksToCommit()) {
                logger.debug("Blocks are still being committed. Wait until commit is done")
                allBlocksCommitted.await()
            }

            // If we did not manage to commit previous block range we unfortunately have to drop this response and retry
            if (stateMachine.hasUnacknowledgedFailedCommit()) {
                logger.debug("Response is irrelevant since previous commit failed")
                stateMachine.acknowledgeFailedCommit()
                stateMachine.resetToWaitForAction(currentTimeMillis())
                return 0
            }

            logger.debug { "Got ${blocks.size} from peer $peerId (starting at height $startingAtHeight)." }
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
                throw ProgrammerMistake("processedBlocks != blocks.size")
            }
            stateMachine.resetToWaitForAction(currentTimeMillis())
            return processedBlocks
        }
    }

    /**
     * @return true if we could extract the header and it was considered valid.
     */
    internal fun handleBlockHeader(
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

        val blockHeader = blockchainConfiguration.decodeBlockHeader(header)
        val peerLastHeight = getHeight(blockHeader)

        if (peerLastHeight != requestedHeight) {
            // Could be a bug
            peerStatuses.maybeBlacklist(peerId, "Slow Sync: Header height=$peerLastHeight, we expected height: $requestedHeight.")
            return null
        }

        val blockWitness = blockchainConfiguration.decodeWitness(witness)
        // If config is mismatching we can't validate witness properly
        // We could potentially verify against signer list in new config, but we can't be sure that we have it yet
        // Anyway, worst case scenario we will simply attempt to load the block and fail
        if (blockHeader.getConfigHash() == null || blockHeader.getConfigHash().contentEquals(blockchainConfiguration.configHash)) {
            val validator = blockchainConfiguration.getBlockHeaderValidator()
            val witnessBuilder = validator.createWitnessBuilderWithoutOwnSignature(blockHeader)
            try {
                validator.validateWitness(blockWitness, witnessBuilder)
            } catch (e: Exception) {
                peerStatuses.maybeBlacklist(peerId, "Slow Sync: Invalid header received (${e.message}). Height: $requestedHeight")
                return null
            }
        }

        logger.trace { "handleBlockHeader() -- Header for height $requestedHeight received" }
        return blockHeader to blockWitness
    }

    internal fun handleBlock(
            peerId: NodeRid,
            header: net.postchain.core.block.BlockHeader,
            witness: BlockWitness,
            height: Long,
            txs: List<ByteArray>
    ) {
        if (header !is BaseBlockHeader) {
            throw BadMessageException("Expected BaseBlockHeader")
        }

        logger.trace { "handleBlock() - Received for height: $height" }
        var bTrace: BlockTrace? = null
        if (logger.isTraceEnabled) {
            logger.trace("handleBlock() - Creating block trace at height: $height")

            bTrace = BlockTrace.build(header.blockRID, height)
        }

        // The witness has already been verified in handleBlockHeader().
        val block = BlockDataWithWitness(header, txs, witness)

        stateMachine.updateUncommittedBlockHeight(height)
        commitBlock(peerId, bTrace, block, height)
    }

    /**
     * NOTE:
     * If one block fails to commit, don't worry about the blocks coming after. This is handled in the BBD.addBlock().
     */
    internal fun commitBlock(peerId: NodeRid, bTrace: BlockTrace?, block: BlockDataWithWitness, height: Long) {
        if (addBlockCompletionFuture?.isDone == true) {
            addBlockCompletionFuture = null // If it's done we don't need the future
        }

        // (this is usually slow and is therefore handled via a future).
        addBlockCompletionFuture = blockDatabase
                .addBlock(block, addBlockCompletionFuture, bTrace)
                .whenCompleteUnwrapped(loggingContext) { _: Any?, exception ->
                    stateMachineLock.withLock {
                        if (exception == null) {
                            logger.debug { "commitBlock() - Block height: $height committed successfully." }
                            try {
                                stateMachine.updateAfterSuccessfulCommit(height)
                                blockHeight.set(height)
                            } catch (t: Throwable) {
                                logger.warn(t) { "Failed to update after successful commit" }
                            }
                        } else {
                            handleAddBlockException(exception, block, bTrace, peerStatuses, peerId)
                            stateMachine.updateAfterFailedCommit(height)
                        }
                        if (!stateMachine.isWaitingForBlocksToCommit()) {
                            allBlocksCommitted.signalAll()
                        }
                    }
                }
    }

    private fun currentTimeMillis() = clock.millis()

    private fun syncDebug(message: String, height: Long) = "syncUntil() -- $message, at height: $height"
}