// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import net.postchain.network.peer.DefaultPeersConnectionStrategy.Companion.SUCCESSFUL_CONNECTION_THRESHOLD
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Clock
import java.time.Duration
import java.time.Instant

class DefaultPeersConnectionStrategyTest {

    companion object : KLogging()

    val peer1 = NodeRid("111111".hexStringToByteArray())
    val peer2 = NodeRid("222222".hexStringToByteArray())
    val peer3 = NodeRid("333333".hexStringToByteArray())
    val peer4 = NodeRid("444444".hexStringToByteArray())
    val peerCaptor = argumentCaptor<NodeRid>()
    val peerCaptor2 = argumentCaptor<NodeRid>()
    val chainCaptor = argumentCaptor<Long>()
    val connMan: PeerConnectionManager = mock()

    fun testConnectAll(me: NodeRid, peerIds: Set<NodeRid>, expectedConns: Set<NodeRid>): DefaultPeersConnectionStrategy {
        val strategy = sut(me)
        strategy.connectAll(0, BlockchainRid.ZERO_RID, peerIds)

        verify(connMan, times(expectedConns.size)).connectChainPeer(chainCaptor.capture(), peerCaptor.capture())
        assertEquals(expectedConns, peerCaptor.allValues.toSet())

        reset(connMan)
        val expectedResidual = peerIds.subtract(expectedConns)
        whenever(connMan.getConnectedNodes(0)).thenReturn(expectedConns.toList())

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            verify(connMan, times(expectedResidual.size)).connectChainPeer(chainCaptor.capture(), peerCaptor2.capture())
            assertEquals(expectedResidual, peerCaptor2.allValues.toSet())
        }
        return strategy
    }

    private fun sut(me: NodeRid, clock: Clock = mock { on { instant() } doReturn Instant.now() }): DefaultPeersConnectionStrategy {
        val strategy = DefaultPeersConnectionStrategy(connMan, me, clock)
        strategy.backupConnTimeMax = 102
        strategy.backupConnTimeMin = 100
        strategy.reconnectTimeMax = 92
        strategy.reconnectTimeMin = 90
        return strategy
    }

    @Test
    fun singleConn() {
        testConnectAll(peer2, setOf(peer1), setOf(peer1))
    }

    @Test
    fun threeConns() {
        testConnectAll(peer4, setOf(peer1, peer2, peer3), setOf(peer1, peer2, peer3))
    }

    @Test
    fun noConns() {
        testConnectAll(peer1, setOf(), setOf())
    }

    @Test
    fun onlySingleBackup() {
        testConnectAll(peer1, setOf(peer2), setOf())
    }

    @Test
    fun onlySingleConnAndSingleBackup() {
        testConnectAll(peer2, setOf(peer1, peer3), setOf(peer1))
    }

    private fun connectionLost(me: NodeRid, peerIds: Set<NodeRid>, lostPeer: NodeRid, outgoing: Boolean) {
        val strategy = testConnectAll(me, peerIds, setOf())
        reset(connMan)
        whenever(connMan.isPeerConnected(0, lostPeer)).thenReturn(false)
        strategy.connectionLost(0, BlockchainRid.ZERO_RID, lostPeer, outgoing)
        Awaitility.await().atMost(400, TimeUnit.MILLISECONDS).untilAsserted {
            verify(connMan).connectChainPeer(0, lostPeer)
        }
    }

    @Test
    fun lostOutgoing() {
        connectionLost(peer1, setOf(peer2, peer3), peer2, true)
    }

    @Test
    fun lostIncoming() {
        connectionLost(peer1, setOf(peer2, peer3), peer3, false)
    }

    @Test
    fun lostConnectionToUnknownPeer() {
        // We don't care about reconnecting to an unknown peer
        val chainId = 0L
        val strategy = sut(peer1)
        strategy.connectAll(chainId, BlockchainRid.ZERO_RID, setOf(peer2))
        strategy.connectionEstablished(chainId, true, peer2)
        // Unknown peer3 connects
        strategy.connectionEstablished(chainId, false, peer3)
        strategy.connectionLost(chainId, BlockchainRid.ZERO_RID, peer3, false)
        sleep(200)
        verify(connMan, never()).connectChainPeer(chainId, peer3)
    }

    @Test
    fun lostAlreadyConnected() {
        val strategy = testConnectAll(peer1, setOf(peer2, peer3), setOf())
        reset(connMan)
        whenever(connMan.isPeerConnected(0, peer3)).thenReturn(true)
        strategy.connectionLost(0, BlockchainRid.ZERO_RID, peer3, true)
        sleep(200)
        verify(connMan, times(0)).connectChainPeer(0, peer3)
    }

    @Test
    fun testSuccessfulConnectionTimeout() {
        val chainId = 0L
        val now = Instant.now()
        val clock: Clock = mock {
            on { instant() } doReturnConsecutively listOf(
                    now, // Used by connectionEstablished
                    now,
                    now + SUCCESSFUL_CONNECTION_THRESHOLD - Duration.ofMillis(500),
                    now + SUCCESSFUL_CONNECTION_THRESHOLD + Duration.ofMillis(500)
            )
        }
        val strategy = sut(peer1, clock)
        strategy.connectAll(chainId, BlockchainRid.ZERO_RID, setOf(peer2))
        Awaitility.await().atMost(400, TimeUnit.MILLISECONDS).untilAsserted {
            verify(connMan).connectChainPeer(0, peer2)
        }
        strategy.connectionEstablished(chainId, true, peer2)
        assertFalse(strategy.isLatestConnectionSuccessful(peer2))
        assertFalse(strategy.isLatestConnectionSuccessful(peer2))
        assertTrue(strategy.isLatestConnectionSuccessful(peer2))

        // Assert that connection is not considered successful if we do not call connectionEstablished
        strategy.connectionLost(chainId, BlockchainRid.ZERO_RID, peer2, true)
        Awaitility.await().atMost(400, TimeUnit.MILLISECONDS).untilAsserted {
            verify(connMan, times(2)).connectChainPeer(0, peer2)
        }
        assertFalse(strategy.isLatestConnectionSuccessful(peer2))
    }
}