package net.postchain.ebft.messages

import io.micrometer.core.instrument.Timer
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.ebft.NodeBlockState
import net.postchain.ebft.NodeStatus
import net.postchain.ebft.message.StateChangeTracker
import net.postchain.metrics.NodeStatusMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.concurrent.TimeUnit

class StateChangeTrackerTest {

    private val nodeRid1 = NodeRid("node1".toByteArray())
    private val nodeRid2 = NodeRid("node2".toByteArray())
    private val nodeRid3 = NodeRid("node3".toByteArray())
    private val appConfig: AppConfig = mock {
        on { pubKey } doReturn "nodePubKey"
        on { trackedEbftMessageMaxKeepTimeMs } doReturn 60
    }
    private val timer: Timer = mock()
    private val nodeStatusMetrics: NodeStatusMetrics = mock {
        on { createStatusChangeTimeTimer(any(), any(), any()) } doReturn timer
    }

    private var currentMs = 10L

    private lateinit var sut: StateChangeTracker

    @BeforeEach
    fun beforeEach() {
        sut = StateChangeTracker(appConfig, nodeStatusMetrics, nanoProvider())
    }

    @Test
    fun `Receive status from other peer which we already have reached should record metric`() {
        // setup
        val status = NodeStatus(0, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid".toByteArray()
        }
        sut.myStatusChange(status)
        addTime(20)
        // execute & verify
        sut.statusChange(nodeRid1, status)
        // verify
        verify(timer).record(20L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Change own status to already registered statuses from other peers should record time for each peer`() {
        // setup
        val status = NodeStatus(0, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid1".toByteArray()
        }
        sut.statusChange(nodeRid1, status)
        addTime(10)
        sut.statusChange(nodeRid2, status)
        addTime(10)
        sut.statusChange(nodeRid3, status)
        addTime(10)
        // execute
        sut.myStatusChange(status)
        // verify
        verify(timer).record(30L, TimeUnit.MILLISECONDS)
        verify(timer).record(20L, TimeUnit.MILLISECONDS)
        verify(timer).record(10L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Registering same status twice should only record metric once`() {
        // setup
        val status = NodeStatus(0, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid".toByteArray()
        }
        sut.myStatusChange(status)
        addTime(10)
        // execute & verify
        sut.statusChange(nodeRid1, status)
        addTime(10)
        sut.statusChange(nodeRid1, status)
        // verify
        verify(timer).record(10L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Incoming same status twice should only record metric once`() {
        // setup
        val status = NodeStatus(0, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid".toByteArray()
        }
        sut.statusChange(nodeRid1, status)
        addTime(10)
        sut.statusChange(nodeRid1, status)
        addTime(10)
        // execute & verify
        sut.myStatusChange(status)
        // verify
        verify(timer).record(20L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Different statuses should be recorded`() {
        // setup
        val status1 = NodeStatus(0, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid1".toByteArray()
        }
        val status2 = NodeStatus(1, 0).apply {
            state = NodeBlockState.Prepared
            blockRID = "rid2".toByteArray()
        }
        val status3 = NodeStatus(2, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid3".toByteArray()
        }
        sut.statusChange(nodeRid1, status1)
        addTime(10)
        sut.statusChange(nodeRid1, status2)
        addTime(10)
        sut.statusChange(nodeRid1, status3)
        addTime(10)
        // execute
        sut.myStatusChange(status1)
        sut.myStatusChange(status2)
        sut.myStatusChange(status3)
        // verify
        verify(timer).record(30L, TimeUnit.MILLISECONDS)
        verify(timer).record(20L, TimeUnit.MILLISECONDS)
        verify(timer).record(10L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `Cleanup should remove old entries`() {
        // setup
        val status1 = NodeStatus(0, 0).apply {
            state = NodeBlockState.HaveBlock
            blockRID = "rid1".toByteArray()
        }
        val status2 = NodeStatus(1, 0).apply {
            state = NodeBlockState.Prepared
            blockRID = "rid2".toByteArray()
        }
        sut.statusChange(nodeRid1, status1)
        addTime(10)
        sut.myStatusChange(status2)
        addTime(100)
        // execute & verify
        sut.statusChange(nodeRid1, status2)
        sut.myStatusChange(status1)
        // verify
        verify(timer, never()).record(any(), eq(TimeUnit.MILLISECONDS))
    }

    private fun addTime(ms: Long) {
        currentMs += ms
    }

    private fun nanoProvider(): () -> Long {
        return { TimeUnit.MILLISECONDS.toNanos(currentMs) }
    }
}
