package net.postchain.ebft.heartbeat

import net.postchain.config.node.NodeConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertFalse

class DefaultHeartbeatListenerTest {

    private val chainId = 0L
    private val now = System.currentTimeMillis()

    @Test
    fun testNoHeartbeatEventRegistered_then_checkFailed() {
        val nodeConfig: NodeConfig = mock()
        val sut = DefaultHeartbeatListener(nodeConfig, chainId)

        // No Heartbeat event registered, then Heartbeat check failed
        assertFalse(sut.checkHeartbeat(now))
    }

    @Test
    fun testHeartbeatCheckPassed() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatTimeout } doReturn 20_000L
        }
        val sut = DefaultHeartbeatListener(nodeConfig, chainId)

        // Register the first Heartbeat event
        sut.onHeartbeat(HeartbeatEvent(now - 10_000L))

        // Heartbeat check passed
        assert(sut.checkHeartbeat(now))
    }

    @Test
    fun testNoHeartbeatEvent_and_timeout_occurs() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatTimeout } doReturn 20_000L
        }
        val sut = DefaultHeartbeatListener(nodeConfig, chainId)

        // Register Heartbeat event
        sut.onHeartbeat(HeartbeatEvent(now - 30_000L))

        // No Heartbeat event registered for the last `timeout` ms, so Heartbeat check failed
        assertFalse(sut.checkHeartbeat(now))
    }

}