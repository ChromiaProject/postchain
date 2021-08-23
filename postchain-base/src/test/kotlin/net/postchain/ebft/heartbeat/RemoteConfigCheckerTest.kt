package net.postchain.ebft.heartbeat

import com.nhaarman.mockitokotlin2.*
import net.postchain.base.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.config.node.NodeConfig
import net.postchain.network.masterslave.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.masterslave.slave.SlaveConnectionManager
import org.junit.Test
import kotlin.test.assertFalse

class RemoteConfigCheckerTest {

    private val chainId = 0L
    private val blockchainRid = BlockchainRid.ZERO_RID
    private val now = System.currentTimeMillis()

    @Test
    fun testNoHeartbeatEventRegistered_then_checkFailed() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (true)
        }
        val connManager: SlaveConnectionManager = mock()
        val sut = RemoteConfigChecker(nodeConfig, chainId, blockchainRid, connManager)

        // No interaction
        // ...

        // Assert: No Heartbeat event registered, then RemoveConfig check failed
        assertFalse(sut.checkHeartbeat(now))

        // Verification: no interaction with connManager
        verify(connManager, never()).requestBlockchainConfig(any())
    }

    @Test
    fun testNoHeartbeatEvent_and_timeout_occurs() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (true)
            on { heartbeatTimeout } doReturn 20_000L
        }
        val connManager: SlaveConnectionManager = mock()
        val sut = RemoteConfigChecker(nodeConfig, chainId, blockchainRid, connManager)

        // Interaction: Register the first Heartbeat event
        sut.onHeartbeat(HeartbeatEvent(now - 30_000L))

        // Assert: No Heartbeat event registered for the last `timeout` ms, so RemoteConfig check failed
        assertFalse(sut.checkHeartbeat(now))

        // Verification: no interaction with connManager
        verify(connManager, never()).requestBlockchainConfig(any())
    }

    @Test
    fun testHeartbeatCheckPassed_intervalCheckFailed_and_configRequested_then_timeoutCheck() {
        val nodeConfig: NodeConfig = mock {
            on { heartbeatEnabled } doReturn (true)
            on { heartbeatTimeout } doReturn 20_000L
            on { remoteConfigEnabled } doReturn (true)
            on { remoteConfigRequestInterval } doReturn 10_000L
            on { remoteConfigTimeout } doReturn 20_000L
        }
        val connManager: SlaveConnectionManager = mock()
        val sut = RemoteConfigChecker(nodeConfig, chainId, blockchainRid, connManager)

        // 1
        // Interaction: Register the first Heartbeat event
        sut.onHeartbeat(HeartbeatEvent(now - 10_000L))

        // Assert: Heartbeat event registered, but no RemoteConfig received so RemoteConfig check FAILED
        assertFalse(sut.checkHeartbeat(now))

        // Verification: remote config requested
        verify(connManager, times(1)).requestBlockchainConfig(eq(chainId))


        // 2
        // Interaction (2): Then remote config received
        val remoteConfig = MsNextBlockchainConfigMessage("aaaa".hexStringToByteArray(), 0L, "bbbb".hexStringToByteArray())
        sut.onMessage(remoteConfig)

        // Assert (2): Heartbeat event registered, RemoteConfig received and RemoteConfig check PASSED
        assert(sut.checkHeartbeat(now))

        // Verification (2): the NEW remote config is not yet requested
        verify(connManager, times(1)).requestBlockchainConfig(eq(chainId))

        val future = now + 15_000L // remoteConfigRequestInterval < future < remoteConfigTimeout
        sut.onHeartbeat(HeartbeatEvent(future))

        // 3
        // Assert (3): Heartbeat event registered, RemoteConfig received and RemoteConfig check PASSED
        assert(sut.checkHeartbeat(future))

        // Verification (3): the NEW remote config requested
        verify(connManager, times(2)).requestBlockchainConfig(eq(chainId))

        val future2 = now + 25_000L // remoteConfigRequestInterval < remoteConfigTimeout < future2
        sut.onHeartbeat(HeartbeatEvent(future2))

        // 4
        // Assert (4): Heartbeat event registered, RemoteConfig received but RemoteConfig check FAILED
        assertFalse(sut.checkHeartbeat(future2))

        // Verification (4): the NEW remote config requested
        verify(connManager, times(3)).requestBlockchainConfig(eq(chainId))
    }

}