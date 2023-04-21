package net.postchain.ebft.syncmanager.common

import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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

        sssm.maybeGetBlockRange(111L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_REPLY, sssm.state)
        assertEquals(0L, sssm.waitForHeight)
        assertEquals(theOnlyOtherNode, sssm.waitForNodeId)

        // Getting 3 blocks (heights: 0,1,2)
        sssm.updateToWaitForCommit(2, 113L)
        assertEquals(SlowSyncStates.WAIT_FOR_COMMIT, sssm.state)
        assertEquals(2L, sssm.lastUncommittedBlockHeight)

        // 3 successful commit
        sssm.updateAfterSuccessfulCommit(0L)
        assertEquals(SlowSyncStates.WAIT_FOR_COMMIT, sssm.state)
        sssm.updateAfterSuccessfulCommit(1L)
        assertEquals(SlowSyncStates.WAIT_FOR_COMMIT, sssm.state)
        sssm.updateAfterSuccessfulCommit(2L)
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)

        // Send another request
        sssm.maybeGetBlockRange(211L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_REPLY, sssm.state)
        assertEquals(3L, sssm.waitForHeight)

        // Getting 1 block (heights: 3)
        sssm.updateToWaitForCommit(3, 213L)
        assertEquals(SlowSyncStates.WAIT_FOR_COMMIT, sssm.state)

        sssm.updateAfterSuccessfulCommit(3L)
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)
    }

    @Test
    fun failing_commit() {
        val sssm = SlowSyncStateMachine(1, SyncParameters())
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state)
        assertEquals(-1L, sssm.lastCommittedBlockHeight)

        sssm.maybeGetBlockRange(111L, ::dummySend)
        assertEquals(SlowSyncStates.WAIT_FOR_REPLY, sssm.state)
        assertEquals(0L, sssm.waitForHeight)
        assertEquals(theOnlyOtherNode, sssm.waitForNodeId)

        // Getting 3 blocks (heights: 0,1,2)
        sssm.updateToWaitForCommit(2, 113L)
        assertEquals(SlowSyncStates.WAIT_FOR_COMMIT, sssm.state)
        assertEquals(2L, sssm.lastUncommittedBlockHeight)

        // 3 first commit fails
        sssm.updateAfterFailedCommit(0L)
        assertEquals(SlowSyncStates.WAIT_FOR_ACTION, sssm.state) // Back to beginning
        assertEquals(-1L, sssm.lastUncommittedBlockHeight) // We are back to square zero
        assertEquals(-1L, sssm.lastCommittedBlockHeight) // We are back to square zero
    }
}