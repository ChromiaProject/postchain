// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import net.postchain.DynamicValueAnswer
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.hexStringToByteArray
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.NodeRid
import net.postchain.network.netty2.ConnectionConfig
import net.postchain.network.peer.DefaultPeersConnectionStrategy.Companion.NETWORK_PEERS_UPDATE_INTERVAL
import net.postchain.network.peer.DefaultPeersConnectionStrategy.Companion.SUCCESSFUL_CONNECTION_THRESHOLD
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.Thread.sleep
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class DefaultPeersConnectionStrategyTest {

    companion object : KLogging()

    private val peer1 = NodeRid("111111".hexStringToByteArray())
    private val peer2 = NodeRid("222222".hexStringToByteArray())
    private val peer3 = NodeRid("333333".hexStringToByteArray())
    private val peer4 = NodeRid("444444".hexStringToByteArray())
    private val peerCaptor = argumentCaptor<NodeRid>()
    private val peerCaptor2 = argumentCaptor<NodeRid>()
    private val chainCaptor = argumentCaptor<Long>()
    private val connMan: PeerConnectionManager = mock()

    private fun testConnectAll(me: NodeRid, peerIds: Set<NodeRid>, expectedConns: Set<NodeRid>): DefaultPeersConnectionStrategy {
        val strategy = sut(me, registeredPeers = peerIds)
        strategy.connectAll(0, ZERO_RID, peerIds)

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

    private fun sut(
            me: NodeRid,
            config: ConnectionConfig = ConnectionConfig(),
            registeredPeers: Set<NodeRid> = emptySet(),
            nodeConfigProvider: NodeConfigurationProvider? = null,
            clock: Clock = mock { on { instant() } doReturn Instant.now() }
    ): DefaultPeersConnectionStrategy {

        val nodeConfigProvider0: NodeConfigurationProvider = nodeConfigProvider ?: run {

            val registeredPeers0 = buildSet {
                add(me)
                addAll(registeredPeers)
            }.associateWith { PeerInfo("host$it", 0, it.data) }

            val nodeConfig: NodeConfig = mock {
                on { peerInfoMap } doReturn registeredPeers0
            }

            mock {
                on { getConfiguration() } doReturn nodeConfig
            }
        }

        return DefaultPeersConnectionStrategy(connMan, me, config, nodeConfigProvider0, clock).apply {
            backupConnTimeMax = 102
            backupConnTimeMin = 100
            reconnectTimeMax = 92
            reconnectTimeMin = 90
        }
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
        strategy.connectionLost(0, ZERO_RID, lostPeer, outgoing)
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
        strategy.connectAll(chainId, ZERO_RID, setOf(peer2))
        strategy.connectionEstablished(chainId, true, peer2)
        // Unknown peer3 connects
        strategy.connectionEstablished(chainId, false, peer3)
        strategy.connectionLost(chainId, ZERO_RID, peer3, false)
        sleep(200)
        verify(connMan, never()).connectChainPeer(chainId, peer3)
    }

    @Test
    fun lostAlreadyConnected() {
        val strategy = testConnectAll(peer1, setOf(peer2, peer3), setOf())
        reset(connMan)
        whenever(connMan.isPeerConnected(0, peer3)).thenReturn(true)
        strategy.connectionLost(0, ZERO_RID, peer3, true)
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
        val strategy = sut(peer1, clock = clock)
        strategy.connectAll(chainId, ZERO_RID, setOf(peer2))
        Awaitility.await().atMost(400, TimeUnit.MILLISECONDS).untilAsserted {
            verify(connMan).connectChainPeer(0, peer2)
        }
        strategy.connectionEstablished(chainId, true, peer2)
        assertFalse(strategy.isLatestConnectionSuccessful(peer2))
        assertFalse(strategy.isLatestConnectionSuccessful(peer2))
        assertTrue(strategy.isLatestConnectionSuccessful(peer2))

        // Assert that connection is not considered successful if we do not call connectionEstablished
        strategy.connectionLost(chainId, ZERO_RID, peer2, true)
        Awaitility.await().atMost(400, TimeUnit.MILLISECONDS).untilAsserted {
            verify(connMan, times(2)).connectChainPeer(0, peer2)
        }
        assertFalse(strategy.isLatestConnectionSuccessful(peer2))
    }

    @Test
    fun `limited number of unknown peers can connect if config property is greater than 0`() {
        val unknownPeers = (0 until 4).map { NodeRid("f0ba0$it".hexStringToByteArray()) }

        val registeredPeers = mutableMapOf(
                peer2 to PeerInfo("host", 0, peer2.data)
        )

        val nodeConfig: NodeConfig = mock {
            on { peerInfoMap } doReturn registeredPeers
        }

        val nodeConfigProvider: NodeConfigurationProvider = mock {
            on { getConfiguration() } doReturn nodeConfig
        }

        val currentMillis = DynamicValueAnswer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC).instant())
        val clock: Clock = mock {
            on { instant() } doAnswer currentMillis
        }

        val strategy = sut(
                peer1,
                ConnectionConfig(maxUnknownPeerConnectionsPerChain = 3),
                nodeConfigProvider = nodeConfigProvider,
                clock = clock
        )

        // only 3 unknown peers can connect
        (0 until 3).forEach {
            reset(connMan)
            whenever(connMan.getConnectedNodes(0)).thenReturn(unknownPeers.subList(0, it))
            assertTrue(strategy.isConnectionAllowed(0, unknownPeers[it]))
        }

        reset(connMan)
        whenever(connMan.getConnectedNodes(0)).thenReturn(unknownPeers.subList(0, 3))

        // the 4th unknown peer can't connect
        assertFalse(strategy.isConnectionAllowed(0, unknownPeers[3]))

        // known peers still can connect
        assertTrue(strategy.isConnectionAllowed(0, peer2))

        // but 4th can connect after being registered
        registeredPeers[unknownPeers[3]] = PeerInfo("host3", 0, unknownPeers[3].data)
        currentMillis.value = currentMillis.value.plus(NETWORK_PEERS_UPDATE_INTERVAL).plusSeconds(1)
        assertTrue(strategy.isConnectionAllowed(0, unknownPeers[3]))

        // 4th peer has been removed but still can connect
        registeredPeers.remove(unknownPeers[3])
        assertTrue(strategy.isConnectionAllowed(0, unknownPeers[3]))
        currentMillis.value = currentMillis.value.plus(NETWORK_PEERS_UPDATE_INTERVAL).plusSeconds(1)
        assertFalse(strategy.isConnectionAllowed(0, unknownPeers[3]))
    }

    @Test
    fun `unlimited number of unknown peers can connect if config property is 0`() {
        val unknownPeers = (0 until 10).map { NodeRid("f0ba0$it".hexStringToByteArray()) }

        val nodeConfig: NodeConfig = mock {
            on { peerInfoMap } doReturn mapOf(peer2 to PeerInfo("host", 0, peer2.data))
        }

        val nodeConfigProvider: NodeConfigurationProvider = mock {
            on { getConfiguration() } doReturn nodeConfig
        }

        val strategy = sut(
                peer1,
                ConnectionConfig(maxUnknownPeerConnectionsPerChain = 0),
                nodeConfigProvider = nodeConfigProvider
        )

        // only 3 unknown peers can connect
        (0 until 10).forEach {
            reset(connMan)
            whenever(connMan.getConnectedNodes(0)).thenReturn(unknownPeers.subList(0, it))
            assertTrue(strategy.isConnectionAllowed(0, unknownPeers[it]))
        }

        // known peers still can connect
        assertTrue(strategy.isConnectionAllowed(0, peer2))
    }
}