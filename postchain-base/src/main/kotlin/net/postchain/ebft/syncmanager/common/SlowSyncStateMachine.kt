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
 * WAIT_FOR_REPLY -> WAIT_FOR_COMMIT, or
 * WAIT_FOR_REPLY -> WAIT_FOR_ACTION (if nothing to commit)
 *
 * 3.
 * WAIT_FOR_COMMIT -> WAIT_FOR_ACTION
 */
class SlowSyncStateMachine(
    val chainIid: Int,
    var state: SlowSyncStates = SlowSyncStates.WAIT_FOR_ACTION,
    var waitForNodeId: NodeRid? = null, // The node we expect to send us an answer
    var waitForHeight: Long? = null, // The height we are waiting for
    var waitTime: Long? = null, // When did we last ask for a BlockRange
    var lastUncommittedBlockHeight: Long = -1L, // Remember: before we have any block the height is -1.
    var lastCommittedBlockHeight: Long = -1L // Only update this after the actual commit
) {

    companion object: KLogging() {

        fun buildWithChain(chainIid: Int): SlowSyncStateMachine {
            val slowSyncStateMachine = SlowSyncStateMachine(chainIid)
            return slowSyncStateMachine
        }
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
        return lastCommittedBlockHeight + 1L
    }

    /**
     * @param nowMs current time in millisecs
     * @param sendRequest is the function we use to send request
     */
    fun maybeGetBlockRange(nowMs: Long, sendRequest: (Long, SlowSyncStateMachine, NodeRid?) -> Unit) {
        when (state) {
            SlowSyncStates.WAIT_FOR_ACTION -> {
                val startingAtHeight = getStartHeight()
                logger.debug { "maybeGetBlockRange() - ChainIid: $chainIid, not waiting for anything, so get height $startingAtHeight and above." }
                sendRequest(startingAtHeight, this, null) // We don't mind asking the old peer
            }
            SlowSyncStates.WAIT_FOR_REPLY -> {
                if (nowMs > (waitTime!! + SlowSyncSleepConst.MAX_PEER_WAIT_TIME_MS)) {
                    // We waited too long, let's ask someone else
                    logger.debug {
                        "maybeGetBlockRange() - ChainIid: $chainIid waited too long, for anything, try again with height: $waitForHeight " +
                                "and above (but don't ask ${NameHelper.peerName(waitForNodeId!!)} )."
                    }
                    state = SlowSyncStates.WAIT_FOR_ACTION // Reset
                    sendRequest(waitForHeight!!, this, waitForNodeId!!)
                } else {
                    logger.debug { "maybeGetBlockRange() - ChainIid: $chainIid still waiting for height: $waitForHeight, go back to sleep." }
                    // Still waiting for last request, go back to sleep
                }
            }
            SlowSyncStates.WAIT_FOR_COMMIT -> {
                logger.debug { "maybeGetBlockRange() - ChainIid: $chainIid do nothing, waiting for height: $lastUncommittedBlockHeight to commit." }
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
        lastUncommittedBlockHeight = startAtHeight - 1
    }

    /**
     * @param heightToCommit - the height of the block we are waiting for to be committed
     */
    fun updateToWaitForCommit(heightToCommit: Long, nowMs: Long) {
        if (state != SlowSyncStates.WAIT_FOR_COMMIT) {
            if (state != SlowSyncStates.WAIT_FOR_REPLY) {
                throw ProgrammerMistake("updateToWaitForCommit(): Incorrect state: $state")
            }
            state = SlowSyncStates.WAIT_FOR_COMMIT
        }

        lastUncommittedBlockHeight = heightToCommit
        waitTime = nowMs
    }

    /**
     * This can be called multiple times, but only when we really get the last block's commit we move to WAIT_FOR_ACTION.
     * This might seem slow but it doesn't matter, since this is slow sync.
     */
    fun updateAfterSuccessfulCommit(committedBlockHeight: Long) {
        if (state != SlowSyncStates.WAIT_FOR_COMMIT) {
            throw ProgrammerMistake("updateAfterSuccessfulCommit(): Incorrect state: $state")
        }

        if (getStartHeight() != committedBlockHeight) {
            logger.warn("updateAfterSuccessfulCommit() - ChainIid: $chainIid something is wrong. Committed height: $committedBlockHeight but last commit was $lastCommittedBlockHeight")
        }

        if (committedBlockHeight > lastUncommittedBlockHeight) {
            throw ProgrammerMistake("Why are we committing higher than we are expecting? " +
                    "Expected: $lastUncommittedBlockHeight but got height: $committedBlockHeight")
        }

        if (committedBlockHeight == lastUncommittedBlockHeight) {
            // Time to move on
            state = SlowSyncStates.WAIT_FOR_ACTION
            waitForNodeId = null
            waitForHeight = null
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
        state = SlowSyncStates.WAIT_FOR_ACTION
        lastUncommittedBlockHeight = lastCommittedBlockHeight
    }

}


enum class SlowSyncStates {
    WAIT_FOR_ACTION,
    WAIT_FOR_REPLY,
    WAIT_FOR_COMMIT
}
