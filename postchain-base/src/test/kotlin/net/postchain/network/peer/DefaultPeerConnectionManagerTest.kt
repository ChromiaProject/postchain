// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.isContentEqualTo
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.NodeRid
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import net.postchain.network.common.ChainsWithConnections
import net.postchain.network.common.ConnectionDirection
import net.postchain.network.common.LazyPacket
import net.postchain.network.netty2.NettyPeerConnection
import net.postchain.network.util.peerInfoFromPublicKey
import org.apache.commons.lang3.reflect.FieldUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class DefaultPeerConnectionManagerTest {

    private val blockchainRid = BlockchainRid.buildRepeat(0x01)

    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerConnectionDescriptor1: PeerConnectionDescriptor
    private lateinit var packetEncoderFactory: XPacketEncoderFactory<Int>
    private lateinit var packetDecoderFactory: XPacketDecoderFactory<Int>

    private lateinit var peerInfo2: PeerInfo
    private lateinit var peerConnectionDescriptor2: PeerConnectionDescriptor

    private lateinit var unknownPeerInfo: PeerInfo

    @BeforeEach
    fun setUp() {
        val b1 = BlockchainRid.buildRepeat(0x01)
        val b2 = BlockchainRid.buildRepeat(0x02)
        val b3 = BlockchainRid.buildRepeat(0x03)
        peerInfo1 = peerInfoFromPublicKey(b1.data)
        peerInfo2 = peerInfoFromPublicKey(b2.data)
        unknownPeerInfo = peerInfoFromPublicKey(b3.data)

        peerConnectionDescriptor1 = PeerConnectionDescriptor(blockchainRid, peerInfo1.peerId(), ConnectionDirection.OUTGOING)
        peerConnectionDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)

        packetEncoderFactory = mock {
            on { create(any(), any()) } doReturn mock()
        }

        packetDecoderFactory = mock {
            on { create(any()) } doReturn mock()
        }
    }

    @Test
    fun connectChain_without_autoConnect() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { myPeerInfo() } doReturn peerInfo1
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply { connectChain(chainPeerConfig, false, mock()) }

        // Then
        verify(communicationConfig, never()).networkNodes

        connectionManager.shutdown()
    }

    @Test
    fun connectChain_with_autoConnect_without_any_peers_will_result_in_exception() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodesDummy()
            on { myPeerInfo() } doReturn peerInfo1
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        )

        try {
            connectionManager.also { it.connectChain(chainPeerConfig, true, mock()) }
        } catch (_: IllegalArgumentException) {
        }

        // Then
        verify(chainPeerConfig, atLeast(1)).chainId
        verify(chainPeerConfig, atLeast(1)).commConfiguration
        verify(chainPeerConfig, atLeast(1)).blockchainRid
        verify(communicationConfig).networkNodes

        connectionManager.shutdown()
    }

    @Test
    fun connectChain_with_autoConnect_with_two_peers() {
        // TODO: [et]: Maybe use arg captor here
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { pubKey } doReturn peerInfo2.pubKey// See DefaultPeersConnectionStrategy
            on { myPeerInfo() } doReturn peerInfo2
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(peerInfo2.pubKey))
            on { resolvePeer(peerInfo1.pubKey) } doReturn peerInfo1
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply { connectChain(chainPeerConfig, true, mock()) }

        // Then
        verify(chainPeerConfig, atLeast(1)).chainId
        verify(chainPeerConfig, atLeast(1)).commConfiguration
        verify(chainPeerConfig, atLeast(1)).blockchainRid

        connectionManager.shutdown()
    }

    @Test
    fun connectChainPeer_will_result_in_exception_if_chain_is_not_connected() {
        assertThrows<ProgrammerMistake> {
            emptyManager().connectChainPeer(1, peerInfo1.peerId())
        }
    }

    @Test
    fun connectChainPeer_connects_unknown_peer_with_exception() {
        // Given
        val communicationConfig: PeerCommConfiguration = emptyCommConf()
        val chainPeerConf = XChainPeersConfiguration(1L, blockchainRid, communicationConfig, mock())

        // Hate mocking
        val d: XPacketDecoder<Int> = mock { }
        val df: XPacketDecoderFactory<Int> = mock {
            on { create(any()) } doReturn d
        }

        // When / Then exception
        assertThrows<ProgrammerMistake> {
            DefaultPeerConnectionManager(
                    mock(),
                    df
            ).apply {
                connectChain(chainPeerConf, false, mock()) // Without connecting to peers
                connectChainPeer(1, unknownPeerInfo.peerId())
            }
        }
    }

    @Test
    fun connectChainPeer_connects_peer_successfully() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { pubKey } doReturn peerInfo1.pubKey
            on { myPeerInfo() } doReturn peerInfo1
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(peerInfo1.pubKey))
            on { resolvePeer(peerInfo2.pubKey) } doReturn peerInfo2
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply {
            connectChain(chainPeerConfig, false, mock()) // Without connecting to peers
            connectChainPeer(1, peerInfo2.peerId())
        }

        // Then
        verify(chainPeerConfig, atLeast(1)).chainId
        verify(chainPeerConfig, atLeast(1)).commConfiguration
        verify(chainPeerConfig, atLeast(1)).blockchainRid

        connectionManager.shutdown()
    }

    @Test
    fun connectChainPeer_connects_already_connected_peer_and_nothing_happens() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { pubKey } doReturn peerInfo2.pubKey // See DefaultPeersConnectionStrategy
            on { myPeerInfo() } doReturn peerInfo2
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(peerInfo2.pubKey))
            on { resolvePeer(peerInfo1.pubKey) } doReturn peerInfo1
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply {
            connectChain(chainPeerConfig, true, mock()) // Auto connect all peers

            // Emulates call of onPeerConnected() by XConnector
            onNodeConnected(mockConnection(peerConnectionDescriptor1))

            connectChainPeer(1, peerInfo1.peerId())
        }

        // Then
        verify(chainPeerConfig, atLeast(3)).chainId
        verify(chainPeerConfig, times(6)).commConfiguration

        connectionManager.shutdown()
    }

    @Test
    fun disconnectChainPeer_will_result_in_exception_if_chain_is_not_connected() {
        assertThrows<ProgrammerMistake> {
            emptyManager().disconnectChainPeer(1L, peerInfo1.peerId())
        }
    }

    @Test
    fun disconnectChain_wont_result_in_exception_if_chain_is_not_connected() {
        emptyManager().disconnectChain(mock(), 1)
    }

    @Test
    fun getConnectedPeers_returns_emptyList_if_chain_is_not_connected() {
        assertThat(emptyManager().getConnectedNodes(1)).isEmpty()
    }

    @Test
    fun isPeerConnected_and_getConnectedPeers_are_succeeded() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { pubKey } doReturn peerInfo2.pubKey // See DefaultPeersConnectionStrategy
            on { myPeerInfo() } doReturn peerInfo2
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(peerInfo2.pubKey))
            on { resolvePeer(peerInfo1.pubKey) } doReturn peerInfo1
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply {
            connectChain(chainPeerConfig, true, mock()) // With autoConnect

            // Then / before peers connected
            // - isPeerConnected
            assertFalse { isPeerConnected(1L, peerInfo1.peerId()) }
            assertFalse { isPeerConnected(1L, peerInfo2.peerId()) }
            assertFalse { isPeerConnected(1L, unknownPeerInfo.peerId()) }
            // - getConnectedPeers
            assertThat(getConnectedNodes(1L).toTypedArray()).isEmpty()

            // Emulates call of onPeerConnected() by XConnector
            onNodeConnected(mockConnection(peerConnectionDescriptor1))
            onNodeConnected(mockConnection(peerConnectionDescriptor2))

            // Then / after peers connected
            // - isPeerConnected
            assertTrue { isPeerConnected(1L, peerInfo1.peerId()) }
            assertTrue { isPeerConnected(1L, peerInfo2.peerId()) }
            assertFalse { isPeerConnected(1L, unknownPeerInfo.peerId()) }
            // - getConnectedPeers
            assertThat(getConnectedNodes(1L).toTypedArray()).isContentEqualTo(
                    arrayOf(peerInfo1.peerId(), peerInfo2.peerId())
            )


            // When / Disconnecting peer1
            disconnectChainPeer(1L, peerInfo1.peerId())
            // Then
            // - isPeerConnected
            assertFalse { isPeerConnected(1L, peerInfo1.peerId()) }
            assertTrue { isPeerConnected(1L, peerInfo2.peerId()) }
            assertFalse { isPeerConnected(1L, unknownPeerInfo.peerId()) }
            // - getConnectedPeers
            assertThat(getConnectedNodes(1L).toTypedArray()).isContentEqualTo(
                    arrayOf(peerInfo2.peerId())
            )


            // When / Disconnecting the whole chain
            disconnectChain(mock(), 1L)
            // Then
            val internalChains = FieldUtils.readField(this, "chainsWithConnections", true)
                    as ChainsWithConnections<*, *, *>
            assertTrue { internalChains.isEmpty() }
        }

        connectionManager.shutdown()
    }

    @Test
    fun sendPacket_will_result_in_exception_if_chain_is_not_connected() {
        assertThrows<ProgrammerMistake> {
            emptyManager().sendPacket({ byteArrayOf() }, 1, peerInfo2.peerId())
        }
    }

    @Test
    fun sendPacket_sends_packet_to_receiver_via_connection_successfully() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { pubKey } doReturn peerInfo2.pubKey // See DefaultPeersConnectionStrategy
            on { myPeerInfo() } doReturn peerInfo2
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(peerInfo2.pubKey))
            on { resolvePeer(peerInfo1.pubKey) } doReturn peerInfo1
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }
        val connection1: NettyPeerConnection = mockConnection(peerConnectionDescriptor1)
        val connection2: NettyPeerConnection = mockConnection(peerConnectionDescriptor2)

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply {
            connectChain(chainPeerConfig, true, mock()) // With autoConnect

            // Emulates call of onPeerConnected() by XConnector
            onNodeConnected(connection1)
            onNodeConnected(connection2)

            sendPacket({ byteArrayOf(0x04, 0x02) }, 1L, peerInfo2.peerId())
        }

        // Then / verify and assert
        verify(connection1, times(0)).sendPacket(any())
        argumentCaptor<LazyPacket>().apply {
            verify(connection2, times(1)).sendPacket(capture())
            assertThat(firstValue()).isContentEqualTo(byteArrayOf(0x04, 0x02))
        }

        connectionManager.shutdown()
    }

    @Test
    fun broadcastPacket_will_result_in_exception_if_chain_is_not_connected() {
        assertThrows<ProgrammerMistake> {
            emptyManager().broadcastPacket({ byteArrayOf() }, 1)
        }
    }

    private fun emptyManager() = DefaultPeerConnectionManager<Int>(mock(), mock())

    private fun emptyCommConf(): PeerCommConfiguration {
        return mock {
            on { myPeerInfo() } doReturn peerInfo1
        }
    }

    @Test
    fun broadcastPacket_sends_packet_to_all_receivers_successfully() {
        // Given
        val communicationConfig: PeerCommConfiguration = mock {
            on { pubKey } doReturn peerInfo1.pubKey
            on { myPeerInfo() } doReturn peerInfo1
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(peerInfo1.pubKey))
            on { resolvePeer(peerInfo2.pubKey) } doReturn peerInfo2
        }
        val chainPeerConfig: XChainPeersConfiguration = mock {
            on { chainId } doReturn 1L
            on { blockchainRid } doReturn blockchainRid
            on { commConfiguration } doReturn communicationConfig
        }
        val connection1: NettyPeerConnection = mockConnection(peerConnectionDescriptor1)
        val connection2: NettyPeerConnection = mockConnection(peerConnectionDescriptor2)

        // When
        val connectionManager = DefaultPeerConnectionManager(
                packetEncoderFactory, packetDecoderFactory
        ).apply {
            connectChain(chainPeerConfig, true, mock()) // With autoConnect

            // Emulates call of onPeerConnected() by XConnector
            onNodeConnected(connection1)
            onNodeConnected(connection2)

            broadcastPacket({ byteArrayOf(0x04, 0x02) }, 1L)
        }

        // Then / verify and assert
        argumentCaptor<LazyPacket>().apply {
            verify(connection1, times(1)).sendPacket(capture())
            assertThat(firstValue()).isContentEqualTo(byteArrayOf(0x04, 0x02))
        }
        argumentCaptor<LazyPacket>().apply {
            verify(connection2, times(1)).sendPacket(capture())
            assertThat(firstValue()).isContentEqualTo(byteArrayOf(0x04, 0x02))
        }

        connectionManager.shutdown()
    }

    fun mockConnection(descriptor: PeerConnectionDescriptor): NettyPeerConnection {
        val m: NettyPeerConnection = mock()
        whenever(m.descriptor()).thenReturn(descriptor)
        return m
    }
}