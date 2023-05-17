// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import org.mockito.kotlin.*
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import net.postchain.network.util.peerInfoFromPublicKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class DefaultPeerCommunicationManagerTest {

    private val blockchainRid = BlockchainRid.buildRepeat(0x01)
    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo

    private val pubKey1 = byteArrayOf(0x01)
    private val pubKey2 = byteArrayOf(0x02)

    companion object {
        private val CHAIN_ID = 1L
    }

    @BeforeEach
    fun setUp() {
        peerInfo1 = peerInfoFromPublicKey(pubKey1)
        peerInfo2 = peerInfoFromPublicKey(pubKey2)
    }

    @Test
    fun successful_construction_of_CommunicationManager_with_no_peers() {
        // Given
        val connectionManager: PeerConnectionManager = mock()
        val peerCommunicationConfig: PeerCommConfiguration = mock {
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodesDummy()
        }
        val packetEncoder: XPacketEncoder<Int> = mock()
        val packetDecoder: XPacketDecoder<Int> = mock()

        // When
        val communicationManager = DefaultPeerCommunicationManager(
                connectionManager, peerCommunicationConfig, CHAIN_ID, blockchainRid, packetEncoder, packetDecoder, mock())
        communicationManager.init()

        // Then
        argumentCaptor<XChainPeersConfiguration>().apply {
            verify(connectionManager).connectChain(capture(), eq(true), any())

            assertThat(firstValue.chainId).isEqualTo(CHAIN_ID)
            assertThat(firstValue.commConfiguration).isSameAs(peerCommunicationConfig)
//            val f: XPacketHandler = { _, _ -> ; } // TODO: Assert function types
//            assertThat(firstValue.packetHandler).isInstanceOf(f.javaClass)
//            assertThat(firstValue.identPacketConverter).isSameAs(packetConverter)
        }

        communicationManager.shutdown()
    }

    @Test
    fun successful_construction_of_CommunicationManager_with_two_peers() {
        // Given
        val connectionManager: PeerConnectionManager = mock()
        val peerCommunicationConfig: PeerCommConfiguration = mock {
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(pubKey1))
            on { resolvePeer(peerInfo1.pubKey) } doReturn peerInfo1
            on { resolvePeer(peerInfo2.pubKey) } doReturn peerInfo2
        }
        val packetEncoder: XPacketEncoder<Int> = mock()
        val packetDecoder: XPacketDecoder<Int> = mock()

        // When
        val communicationManager = DefaultPeerCommunicationManager(
                connectionManager, peerCommunicationConfig, CHAIN_ID, blockchainRid, packetEncoder, packetDecoder, mock())
        communicationManager.init()

        // Then
        argumentCaptor<XChainPeersConfiguration>().apply {
            verify(connectionManager).connectChain(capture(), eq(true), any())

            assertThat(firstValue.chainId).isEqualTo(CHAIN_ID)
            assertThat(firstValue.commConfiguration).isSameAs(peerCommunicationConfig)
//            val f: XPacketHandler = { _, _ -> ; } // TODO: Assert function types
//            assertThat(firstValue.packetHandler).isInstanceOf(f.javaClass)
//            assertThat(firstValue.identPacketConverter).isSameAs(packetConverter)
        }

        communicationManager.shutdown()
    }

    @Test
    fun sendPacket_will_result_in_exception_if_my_NodeRid_was_given() {
        // Given
        val peersConfig: PeerCommConfiguration = mock {
            on { networkNodes } doReturn NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), NodeRid(pubKey2))
            on { pubKey } doReturn pubKey1
        }

        // When / Then exception
        assertThrows<IllegalArgumentException> {
            DefaultPeerCommunicationManager<Int>(mock(), peersConfig, CHAIN_ID, blockchainRid, mock(), mock(), mock())
                .apply {
                    sendPacket(0, NodeRid(pubKey1))
                }
        }
    }

    @Test
    fun sendPacket_sends_packet_successfully() {
        // Given
        val peerInfo1Mock: PeerInfo = spy(peerInfo1)
        val connectionManager: PeerConnectionManager = mock()
        val config = object : PeerCommConfigurationDummy() {
            override val networkNodes = NetworkNodes.buildNetworkNodes(setOf(peerInfo1Mock, peerInfo2), NodeRid(pubKey2))
            override val pubKey = pubKey2
        }

        // When
        val communicationManager = DefaultPeerCommunicationManager<Int>(
                connectionManager, config, CHAIN_ID, blockchainRid, mock(), mock(), mock()
        )
                .apply {
                    init()
                    sendPacket(0, NodeRid(pubKey1))
                }

        // Then
        verify(connectionManager).sendPacket(any(), eq(CHAIN_ID), eq(peerInfo1.peerId()))
        //verify(peerCommunicationConfig, times(1)).networkNodes
        verify(peerInfo1Mock).pubKey

        communicationManager.shutdown()
    }

    @Test
    fun broadcastPacket_sends_packet_successfully() {
        // Given
        val connectionManager: PeerConnectionManager = mock()

        // When
        val communicationManager = DefaultPeerCommunicationManager<Int>(
                connectionManager, mock(), CHAIN_ID, blockchainRid, mock(), mock(), mock()
        )
                .apply {
                    init()
                    broadcastPacket(42)
                }

        // Then
        verify(connectionManager).broadcastPacket(any(), eq(CHAIN_ID))

        communicationManager.shutdown()
    }

    @Test
    fun shutdown_successfully_disconnects_chain() {
        // Given
        val connectionManager: PeerConnectionManager = mock()

        // When
        val communicationManager = DefaultPeerCommunicationManager<Int>(
                connectionManager, mock(), CHAIN_ID, blockchainRid, mock(), mock(), mock()
        )
                .apply {
                    init()
                    shutdown()
                }

        // Then
        verify(connectionManager).disconnectChain(any(), eq(CHAIN_ID))

        communicationManager.shutdown()
    }
}