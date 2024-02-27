package net.postchain.ebft

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.micrometer.core.instrument.Counter
import io.opentelemetry.api.trace.Tracer
import net.postchain.common.hexStringToByteArray
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
    private val ebftTracer: EbftStateTracer = mock()
    private val clock: Clock = mock {
        on { millis() } doReturn BaseStatusManager.ZERO_SERIAL_TIME
    }

    private lateinit var sut: BaseStatusManager
    private lateinit var status: NodeStatus

    @BeforeEach
    fun beforeEach() {
        status = NodeStatus(54, 0)
        status.revolting = true
        sut = BaseStatusManager(nodes, myNodeIndex, myNextHeight, nodeStatusMetrics, stateChangeTracker, ebftTracer, clock)
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
}