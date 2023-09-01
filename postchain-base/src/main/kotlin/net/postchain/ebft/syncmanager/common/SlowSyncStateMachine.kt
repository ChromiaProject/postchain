package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper


/**
 * Slow sync can only be in one state at a time. The allowed state changes are:
 *
 * 1.
 * WAIT_FOR_ACTION -> WAIT_FOR_REPLY
 *
 * 2.
 * WAIT_FOR_REPLY -> WAIT_FOR_ACTION
 */
class SlowSyncStateMachine(
        val chainIid: Int,
        val params: SyncParameters,
        var state: SlowSyncStates = SlowSyncStates.WAIT_FOR_ACTION,
        var waitForNodeId: NodeRid? = null, // The node we expect to send us an answer
        var waitForHeight: Long? = null, // The height we are waiting for
        private var waitTime: Long? = null, // When did we last ask for a BlockRange
        private var idleTime: Long = 0, // When did we start waiting to ask for a BlockRange
        var lastUncommittedBlockHeight: Long = -1L, // Remember: before we have any block the height is -1.
        var lastCommittedBlockHeight: Long = -1L // Only update this after the actual commit
) {
    private var hasFailedCommit = false

    companion object : KLogging() {
        fun buildWithChain(chainIid: Int, params: SyncParameters) = SlowSyncStateMachine(chainIid, params)
    }

    override fun toString(): String {
        return "(chainIid: $chainIid, state: $state, waitForPeer: $waitForNodeId, waitForHeight: $waitForHeight, " +
                "last uncommitted h: $lastUncommittedBlockHeight, last committed h: $lastCommittedBlockHeight)"
    }

    fun isHeightWeWaitingFor(height: Long): Boolean {
        return height == waitForHeight
    }

    fun isPeerWeAreWaitingFor(peer: NodeRid): Boolean {
        return peer == waitForNodeId
    }

    fun getStartHeight(): Long {
        return lastUncommittedBlockHeight + 1L
    }

    /**
     * @param nowMs current time in millisecs
     * @param currentSleepMs how long we should wait until sending a new request
     * @param sendRequest is the function we use to send request
     */
    fun maybeGetBlockRange(nowMs: Long, currentSleepMs: Long, sendRequest: (Long, SlowSyncStateMachine, NodeRid?) -> Unit) {
        when (state) {
            SlowSyncStates.WAIT_FOR_ACTION -> {
                if (nowMs > (idleTime + currentSleepMs)) {
                    val startingAtHeight = getStartHeight()
                    logger.debug { "maybeGetBlockRange() - done idling, so get height $startingAtHeight and above." }
                    sendRequest(nowMs, this, null) // We don't mind asking the old peer
                }
            }

            SlowSyncStates.WAIT_FOR_REPLY -> {
                if (nowMs > (waitTime!! + params.slowSyncMaxPeerWaitTime)) {
                    // We waited too long, let's ask someone else
                    logger.debug {
                        "maybeGetBlockRange() - waited too long, for anything, try again with height: $waitForHeight " +
                                "and above (but don't ask ${NameHelper.peerName(waitForNodeId!!)} )."
                    }
                    state = SlowSyncStates.WAIT_FOR_ACTION // Reset
                    sendRequest(nowMs, this, waitForNodeId!!)
                } else {
                    logger.debug { "maybeGetBlockRange() - still waiting for height: $waitForHeight, go back to sleep." }
                    // Still waiting for last request, go back to sleep
                }
            }
        }
    }

    fun updateToWaitForReply(peer: NodeRid, startAtHeight: Long, nowMs: Long) {
        if (state != SlowSyncStates.WAIT_FOR_ACTION) {
            throw ProgrammerMistake("updateToWaitForReply(): Incorrect state: $state")
        }

        state = SlowSyncStates.WAIT_FOR_REPLY
        waitForNodeId = peer
        waitForHeight = startAtHeight
        waitTime = nowMs
    }

    /**
     * @param heightToCommit - the height of the block we are waiting for to be committed
     */
    fun updateUncommittedBlockHeight(heightToCommit: Long) {
        if (heightToCommit < lastUncommittedBlockHeight) {
            throw ProgrammerMistake("Trying to update uncommitted block height to a lower height than we are already waiting for")
        }

        lastUncommittedBlockHeight = heightToCommit
    }

    /**
     * This can be called multiple times, but only when we really get the last block's commit we move to WAIT_FOR_ACTION.
     * This might seem slow but it doesn't matter, since this is slow sync.
     */
    fun updateAfterSuccessfulCommit(committedBlockHeight: Long) {
        if (lastCommittedBlockHeight + 1 != committedBlockHeight) {
            logger.warn("updateAfterSuccessfulCommit() - ChainIid: $chainIid something is wrong. Committed height: $committedBlockHeight but last commit was $lastCommittedBlockHeight")
        }

        if (committedBlockHeight > lastUncommittedBlockHeight) {
            throw ProgrammerMistake("Why are we committing higher than we are expecting? " +
                    "Expected: $lastUncommittedBlockHeight but got height: $committedBlockHeight")
        }

        logger.debug { "updateAfterSuccessfulCommit() - Prev last committed height: $lastCommittedBlockHeight , now: $committedBlockHeight, $this" }
        lastCommittedBlockHeight = committedBlockHeight

    }

    /**
     * After one commit failed, all the following will crash too, since they are depending on the failed block
     * Best is to ignore everything from here on and move back to last known successful commit
     */
    fun updateAfterFailedCommit(committedBlockHeight: Long) {
        logger.warn("ChainIid: $chainIid block height: $committedBlockHeight failed, last successful was $lastCommittedBlockHeight.")
        lastUncommittedBlockHeight = lastCommittedBlockHeight
        hasFailedCommit = true
    }

    fun isWaitingForBlocksToCommit() = lastCommittedBlockHeight < lastUncommittedBlockHeight

    fun resetToWaitForAction(nowMs: Long) {
        state = SlowSyncStates.WAIT_FOR_ACTION
        waitForNodeId = null
        waitForHeight = null
        idleTime = nowMs
    }

    fun hasUnacknowledgedFailedCommit() = hasFailedCommit

    fun acknowledgeFailedCommit() {
        hasFailedCommit = false
    }
}

enum class SlowSyncStates {
    WAIT_FOR_ACTION,
    WAIT_FOR_REPLY
}