package net.postchain.ebft.heartbeat

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import net.postchain.config.node.NodeConfig
import org.junit.Test
import kotlin.test.assertFalse

class DefaultHeartbeatCheckerTest {

    private val chainId = 0L
    private val now = System.currentTimeMillis()

    @Test
    fun testHeartbeatIsDisabled_then_checkPassed() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (false)
        }
        val sut = DefaultHeartbeatChecker(nodeConfig, chainId)

        // No Heartbeat event registered, but Heartbeat Check passed
        assert(sut.checkHeartbeat(now))
    }

    @Test
    fun testNoHeartbeatEventRegistered_then_checkFailed() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (true)
        }
        val sut = DefaultHeartbeatChecker(nodeConfig, chainId)

        // No Heartbeat event registered, then Heartbeat check failed
        assertFalse(sut.checkHeartbeat(now))
    }

    @Test
    fun testHeartbeatCheckPassed() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (true)
            on { heartbeatTimeout } doReturn 20_000L
        }
        val sut = DefaultHeartbeatChecker(nodeConfig, chainId)

        // Register the first Heartbeat event
        sut.onHeartbeat(HeartbeatEvent(now - 10_000L))

        // Heartbeat check passed
        assert(sut.checkHeartbeat(now))
    }

    @Test
    fun testNoHeartbeatEvent_and_timeout_occurs() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (true)
            on { heartbeatTimeout } doReturn 20_000L
        }
        val sut = DefaultHeartbeatChecker(nodeConfig, chainId)

        // Register Heartbeat event
        sut.onHeartbeat(HeartbeatEvent(now - 30_000L))

        // No Heartbeat event registered for the last `timeout` ms, so Heartbeat check failed
        assertFalse(sut.checkHeartbeat(now))
    }

}