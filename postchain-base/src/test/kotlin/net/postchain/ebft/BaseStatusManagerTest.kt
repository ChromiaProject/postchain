package net.postchain.ebft

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.micrometer.core.instrument.Counter
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.Signature
import net.postchain.ebft.message.StateChangeTracker
import net.postchain.metrics.NodeStatusMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Clock

class BaseStatusManagerTest {

    private val node1 = "1111111111111111111111111111111111111111111111111111111111111111".hexStringToByteArray()
    private val node2 = "2222222222222222222222222222222222222222222222222222222222222222".hexStringToByteArray()
    private val myNode = "3333333333333333333333333333333333333333333333333333333333333333".hexStringToByteArray()
    private val nodes = listOf(node1, myNode, node2)
    private val node1Index: Int = 0
    private val myNodeIndex: Int = 1
    private val myNextHeight: Long = 42
    private val revoltsOnNodeCounter: Counter = mock()
    private val revoltsByNodeCounter: Counter = mock()
    private val revoltsBetweenOthersCounter: Counter = mock()
    private val nodeStatusMetrics: NodeStatusMetrics = mock {
        on { revoltsOnNode } doReturn revoltsOnNodeCounter
        on { revoltsByNode } doReturn revoltsByNodeCounter
        on { revoltsBetweenOthers } doReturn revoltsBetweenOthersCounter
    }
    private val stateChangeTracker: StateChangeTracker = mock()
    private val clock: Clock = mock {
        on { millis() } doReturn BaseStatusManager.ZERO_SERIAL_TIME
    }

    private lateinit var sut: BaseStatusManager
    private lateinit var status: NodeStatus

    @BeforeEach
    fun beforeEach() {
        status = NodeStatus(54, 0)
        status.revolting = true
        sut = BaseStatusManager(nodes, myNodeIndex, myNextHeight, nodeStatusMetrics, stateChangeTracker, clock)
        sut.myStatus.height = 54
    }

    @Test
    fun `reportRevolt when not at current height should do nothing`() {
        // setup
        sut.myStatus.height = 53
        assertThat(sut.nodeReportedRevolt[node1Index]).isFalse()
        // execute
        sut.reportRevolt(node1Index, status)
        // verify
        assertThat(sut.nodeReportedRevolt[node1Index]).isFalse()
    }

    @Test
    fun `reportRevolt when revolting between other nodes should log and increase revolt between nodes metric`() {
        // setup
        status.round = 2
        assertThat(sut.nodeReportedRevolt[node1Index]).isFalse()
        // execute
        sut.reportRevolt(node1Index, status)
        // verify
        verify(revoltsBetweenOthersCounter).increment()
        assertThat(sut.nodeReportedRevolt[node1Index]).isTrue()
    }

    @Test
    fun `reportRevolt when revolting should not log multiple times`() {
        // setup
        status.round = 2
        assertThat(sut.nodeReportedRevolt[node1Index]).isFalse()
        // execute
        sut.reportRevolt(node1Index, status)
        assertThat(sut.nodeReportedRevolt[node1Index]).isTrue()
        sut.reportRevolt(node1Index, status)
        // verify
        verify(revoltsBetweenOthersCounter, times(1)).increment()
    }

    @Test
    fun `reportRevolt when revolting on current node should log and increase revolt on node metric`() {
        // setup
        status.round = 1
        // execute
        sut.reportRevolt(node1Index, status)
        // verify
        verify(revoltsOnNodeCounter).increment()
    }

    @Test
    fun `logRevolt when current node is revolting should log and increase revolt by node metric`() {
        // setup
        status.round = 0
        // execute
        sut.logRevolt(myNodeIndex, status)
        // verify
        verify(revoltsByNodeCounter).increment()
    }

    @Test
    fun `prepared node fetches block at current height when peers are ahead`() {
        val node4 = "4444444444444444444444444444444444444444444444444444444444444444".hexStringToByteArray()
        val manager = BaseStatusManager(
                listOf(node1, node2, myNode, node4),
                2,
                10,
                nodeStatusMetrics,
                stateChangeTracker,
                clock
        )
        val blockRID = ByteArray(32) { 1 }
        manager.myStatus.state = NodeBlockState.Prepared
        manager.myStatus.blockRID = blockRID
        manager.nodeStatuses[0].height = 11

        manager.recomputeStatus()

        assertThat(manager.intent).isEqualTo(FetchBlockAtHeightIntent(10))
        assertThat(manager.myStatus.state).isEqualTo(NodeBlockState.Prepared)
    }

    @Test
    fun `prepared block is persisted before reaching prepared state`() {
        val node4 = "4444444444444444444444444444444444444444444444444444444444444444".hexStringToByteArray()
        val store = InMemoryPreparedBlockStore()
        val manager = BaseStatusManager(
                listOf(node1, node2, myNode, node4),
                2,
                10,
                nodeStatusMetrics,
                stateChangeTracker,
                clock,
                store
        )
        val blockRID = ByteArray(32) { 2 }
        val signature = Signature(ByteArray(33) { 3 }, ByteArray(64) { 4 })
        manager.myStatus.height = 10
        manager.myStatus.state = NodeBlockState.HaveBlock
        manager.myStatus.blockRID = blockRID
        manager.myStatus.round = 0
        manager.commitSignatures[2] = signature
        manager.nodeStatuses[0].height = 10
        manager.nodeStatuses[0].state = NodeBlockState.HaveBlock
        manager.nodeStatuses[0].blockRID = blockRID
        manager.nodeStatuses[0].round = 0
        manager.nodeStatuses[1].height = 10
        manager.nodeStatuses[1].state = NodeBlockState.Prepared
        manager.nodeStatuses[1].blockRID = blockRID
        manager.nodeStatuses[1].round = 0

        manager.recomputeStatus()

        assertThat(manager.myStatus.state).isEqualTo(NodeBlockState.Prepared)
        assertThat(store.saved[10]).isEqualTo(blockRID)
    }

    @Test
    fun `reloaded prepared lock refuses conflicting block at same height`() {
        val node4 = "4444444444444444444444444444444444444444444444444444444444444444".hexStringToByteArray()
        val store = InMemoryPreparedBlockStore()
        store.saved[10] = ByteArray(32) { 5 }
        val manager = BaseStatusManager(
                listOf(node1, node2, myNode, node4),
                2,
                10,
                nodeStatusMetrics,
                stateChangeTracker,
                clock,
                store
        )
        val conflictingRID = ByteArray(32) { 6 }
        val signature = Signature(ByteArray(33) { 7 }, ByteArray(64) { 8 })

        assertThat(manager.acceptBlock(conflictingRID, signature)).isFalse()
        assertThat(manager.myStatus.blockRID).isEqualTo(null)
    }

    private class InMemoryPreparedBlockStore : PreparedBlockStore {
        val saved = mutableMapOf<Long, ByteArray>()

        override fun initialize() = Unit
        override fun load(height: Long): ByteArray? = saved[height]
        override fun save(height: Long, blockRID: ByteArray): Boolean {
            saved[height] = blockRID
            return true
        }
    }
}