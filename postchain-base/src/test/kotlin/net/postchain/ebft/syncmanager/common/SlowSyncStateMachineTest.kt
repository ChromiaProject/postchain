package net.postchain.ebft.syncmanager.common

import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@Suppress("UNUSED_PARAMETER")
class SlowSyncStateMachineTest {

    val nodeHex = "121212"
    val theOnlyOtherNode = NodeRid(nodeHex.hexStringToByteArray())

    fun dummySend(nowMs: Long, sssm: SlowSyncStateMachine, node: NodeRid? = null) {
        val height = sssm.getStartHeight()
        println("sending request for $height")
        sssm.updateToWaitForReply(theOnlyOtherNode, height, 1L)
    }

    @Test
    fun happy() {
        val sssm = SlowSyncStateMachine(1, SyncParameters())
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)
        assertEquals(-1L, sssm.lastCommittedBlockHeight)

        sssm.maybeGetBlockRange(111L, 40L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_REPLY, sssm.state)
        assertEquals(0L, sssm.waitForHeight)
        assertEquals(theOnlyOtherNode, sssm.waitForNodeId)

        // Getting 3 blocks (heights: 0,1,2)
        sssm.updateUncommittedBlockHeight(2)
        sssm.resetToWaitForAction(150L)
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)
        assertEquals(2L, sssm.lastUncommittedBlockHeight)

        // 3 successful commit
        sssm.updateAfterSuccessfulCommit(0L)
        sssm.updateAfterSuccessfulCommit(1L)
        sssm.updateAfterSuccessfulCommit(2L)
        assertEquals(sssm.lastCommittedBlockHeight, sssm.lastUncommittedBlockHeight)

        // Should wait for timeout
        sssm.maybeGetBlockRange(160L, 40L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)

        // Send another request
        sssm.maybeGetBlockRange(211L, 40L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_REPLY, sssm.state)
        assertEquals(3L, sssm.waitForHeight)

        // Getting 1 block (heights: 3)
        sssm.updateUncommittedBlockHeight(3)
        sssm.resetToWaitForAction(250L)

        sssm.updateAfterSuccessfulCommit(3L)
        assertEquals(sssm.lastCommittedBlockHeight, sssm.lastUncommittedBlockHeight)
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)
    }

    @Test
    fun failing_commit() {
        val sssm = SlowSyncStateMachine(1, SyncParameters())
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)
        assertEquals(-1L, sssm.lastCommittedBlockHeight)

        sssm.maybeGetBlockRange(111L, 40L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_REPLY, sssm.state)
        assertEquals(0L, sssm.waitForHeight)
        assertEquals(theOnlyOtherNode, sssm.waitForNodeId)

        // Getting 3 blocks (heights: 0,1,2)
        sssm.updateUncommittedBlockHeight(2)
        sssm.resetToWaitForAction(150L)
        assertEquals(2L, sssm.lastUncommittedBlockHeight)

        // 3 first commit fails
        sssm.updateAfterFailedCommit(0L)
        assertTrue(sssm.hasUnacknowledgedFailedCommit())
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state) // Back to beginning
        assertEquals(-1L, sssm.lastUncommittedBlockHeight) // We are back to square zero
        assertEquals(-1L, sssm.lastCommittedBlockHeight) // We are back to square zero
    }
}