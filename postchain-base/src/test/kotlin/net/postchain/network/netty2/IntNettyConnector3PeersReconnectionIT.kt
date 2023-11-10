// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.network.common.ConnectionDirection
import net.postchain.network.peer.PeerConnection
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.util.peerInfoFromPublicKey
import org.awaitility.Awaitility.await
import org.awaitility.Duration.FIVE_SECONDS
import org.awaitility.Duration.TEN_SECONDS
import org.awaitility.kotlin.withPollDelay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Based on [IntNettyConnector3PeersCommunicationIT]
 */
class IntNettyConnector3PeersReconnectionIT {

    private val blockchainRid = BlockchainRid.buildRepeat(0x01)
    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo
    private lateinit var peerInfo3: PeerInfo
    private lateinit var context1: IntTestContext
    private lateinit var context2: IntTestContext
    private lateinit var context3: IntTestContext

    @BeforeEach
    fun setUp() {
        peerInfo1 = peerInfoFromPublicKey(byteArrayOf(0, 0, 0, 1))
        peerInfo2 = peerInfoFromPublicKey(byteArrayOf(0, 0, 0, 2))
        peerInfo3 = peerInfoFromPublicKey(byteArrayOf(0, 0, 0, 3))

        // Starting contexts
        context1 = startContext(peerInfo1)
        context2 = startContext(peerInfo2)
        context3 = startContext(peerInfo3)
    }

    @AfterEach
    fun tearDown() {
        stopContext(context1)
        stopContext(context2)
        stopContext(context3)
    }

    private fun startContext(peerInfo: PeerInfo): IntTestContext {
        return IntTestContext(peerInfo, arrayOf(peerInfo1, peerInfo2, peerInfo3))
                .also {
                    it.peer.init(peerInfo, it.packetCodec)
                }
    }

    private fun stopContext(context: IntTestContext) {
        context.shutdown()
    }

    @Test
    fun testConnectDisconnectAndConnectAgain() {
        // Connecting
        // * 1 -> 2
        val peerDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor2, peerInfo2, context1.packetCodec)
        // * 1 -> 3
        val peerDescriptor3 = PeerConnectionDescriptor(blockchainRid, peerInfo3.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor3, peerInfo3, context1.packetCodec)
        // * 3 -> 2
        context3.peer.connectNode(peerDescriptor2, peerInfo2, context3.packetCodec)

        // Waiting for all connections to be established
        val connection1 = argumentCaptor<PeerConnection>()
        val connection2 = argumentCaptor<PeerConnection>()
        val connection3 = argumentCaptor<PeerConnection>()
        await().atMost(FIVE_SECONDS)
                .untilAsserted {
                    // 1
                    val expected1 = arrayOf(peerInfo2, peerInfo3).map(PeerInfo::peerId).toTypedArray()
                    verify(context1.events, times(2)).onNodeConnected(connection1.capture())
                    assertThat(connection1.firstValue.descriptor().nodeId).isIn(*expected1)
                    assertThat(connection1.secondValue.descriptor().nodeId).isIn(*expected1)

                    // 2
                    val expected2 = arrayOf(peerInfo1, peerInfo3).map(PeerInfo::peerId).toTypedArray()
                    verify(context2.events, times(2)).onNodeConnected(connection2.capture())
                    assertThat(connection2.firstValue.descriptor().nodeId).isIn(*expected2)
                    assertThat(connection2.secondValue.descriptor().nodeId).isIn(*expected2)

                    // 3
                    val expected3 = arrayOf(peerInfo1, peerInfo2).map(PeerInfo::peerId).toTypedArray()
                    verify(context3.events, times(2)).onNodeConnected(connection3.capture())
                    assertThat(connection3.firstValue.descriptor().nodeId).isIn(*expected3)
                    assertThat(connection3.secondValue.descriptor().nodeId).isIn(*expected3)
                }

        // Disconnecting: peer3 disconnects from peer1 and peer2
        stopContext(context3)

        val connectionCapture1 = argumentCaptor<PeerConnection>()
        val connectionCapture2 = argumentCaptor<PeerConnection>()
        await().atMost(TEN_SECONDS)
                .untilAsserted {
                    // Asserting peer3 is disconnected from peer1
                    verify(context1.events, times(1)).onNodeDisconnected(connectionCapture1.capture())
                    assertThat(connectionCapture1.firstValue.descriptor().nodeId).isEqualTo(peerInfo3.peerId())

                    // Asserting peer3 is disconnected from peer2
                    verify(context2.events, times(1)).onNodeDisconnected(connectionCapture2.capture())
                    assertThat(connectionCapture2.firstValue.descriptor().nodeId).isEqualTo(peerInfo3.peerId())
                }

        // Sending packets
        // * 1 -> 2 and 1 -> 3
        val packet1 = byteArrayOf(10, 2, 3, 4)
        connection1.firstValue.sendPacket(lazy { packet1 })
        connection1.secondValue.sendPacket(lazy { packet1 })

        // Asserting peer2 have received packet1
        await().atMost(FIVE_SECONDS)
                .untilAsserted {
                    // Peer2
                    val packets2 = argumentCaptor<ByteArray>()
                    verify(context2.packets, times(3)).handle(packets2.capture(), any())
                    assertThat(packets2.firstValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                    assertThat(packets2.secondValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                    assertThat(packets2.thirdValue.wrap()).isEqualTo(packet1.wrap())
                }

        // Asserting peer3 haven't received packet1
        await().withPollDelay(FIVE_SECONDS)
                .atMost(FIVE_SECONDS.multiply(2))
                .untilAsserted {
                    // Peer3
                    val packets3 = argumentCaptor<ByteArray>()
                    verify(context3.packets, times(2)).handle(packets3.capture(), any())
                    assertThat(packets3.firstValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                    assertThat(packets3.secondValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                }

        // Re-borning of peer3
        context3 = startContext(peerInfo3)

        // Re-connecting
        // * 3 -> 1
        val peerDescriptor1 = PeerConnectionDescriptor(blockchainRid, peerInfo1.peerId(), ConnectionDirection.OUTGOING)
        context3.peer.connectNode(peerDescriptor1, peerInfo1, context3.packetCodec)
        // * 3 -> 2
        context3.peer.connectNode(peerDescriptor2, peerInfo2, context3.packetCodec)

        // Waiting for all connections to be established
        val connection1_2 = argumentCaptor<PeerConnection>()
        val connection2_2 = argumentCaptor<PeerConnection>()
        val connection3_2 = argumentCaptor<PeerConnection>()
        await().atMost(FIVE_SECONDS)
                .untilAsserted {
                    // 1
                    verify(context1.events, times(3)).onNodeConnected(connection1_2.capture())
                    assertThat(connection1_2.thirdValue.descriptor().nodeId).isEqualTo(peerInfo3.peerId())

                    // 2
                    verify(context2.events, times(3)).onNodeConnected(connection2_2.capture())
                    assertThat(connection2_2.thirdValue.descriptor().nodeId).isEqualTo(peerInfo3.peerId())

                    // 3
                    val expected3 = arrayOf(peerInfo1, peerInfo2).map(PeerInfo::peerId).toTypedArray()
                    verify(context3.events, times(2)).onNodeConnected(connection3_2.capture())
                    assertThat(connection3_2.firstValue.descriptor().nodeId).isIn(*expected3)
                    assertThat(connection3_2.secondValue.descriptor().nodeId).isIn(*expected3)
                }

        // Sending packets
        // * 1 -> 3
        val packet1_2 = byteArrayOf(10, 2, 3, 4)
        connection1_2.thirdValue.sendPacket(lazy { packet1_2 })
        // * 2 -> 3
        val packet2_2 = byteArrayOf(1, 20, 3, 4)
        connection2_2.thirdValue.sendPacket(lazy { packet2_2 })
        // * 3 -> 1 and 3 -> 2
        val packet3_2 = byteArrayOf(1, 2, 30, 4)
        connection3_2.firstValue.sendPacket(lazy { packet3_2 })
        connection3_2.secondValue.sendPacket(lazy { packet3_2 })

        // * asserting
        await().atMost(TEN_SECONDS)
                .untilAsserted {
                    // Peer1
                    val packets1 = argumentCaptor<ByteArray>()
                    verify(context1.packets, times(4)).handle(packets1.capture(), any())
                    assertThat(packets1.firstValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                    assertThat(packets1.secondValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                    assertThat(packets1.thirdValue.wrap()).isEqualTo(INT_PACKET_VERSION_ARRAY.wrap())
                    assertThat(packets1.allValues[3].wrap()).isEqualTo(packet3_2.wrap())

                    // Peer2
                    val packets2 = argumentCaptor<ByteArray>()
                    val expected2 = arrayOf(packet1, packet3_2, INT_PACKET_VERSION_ARRAY).map(ByteArray::wrap).toTypedArray()
                    verify(context2.packets, times(5)).handle(packets2.capture(), any())
                    assertThat(packets2.firstValue.wrap()).isIn(*expected2)
                    assertThat(packets2.secondValue.wrap()).isIn(*expected2)
                    assertThat(packets2.thirdValue.wrap()).isIn(*expected2)
                    assertThat(packets2.allValues[3].wrap()).isIn(*expected2)
                    assertThat(packets2.allValues[4].wrap()).isIn(*expected2)

                    // Peer3
                    val packets3 = argumentCaptor<ByteArray>()
                    val expected3 = arrayOf(packet1_2, packet2_2, INT_PACKET_VERSION_ARRAY).map(ByteArray::wrap).toTypedArray()
                    verify(context3.packets, times(4)).handle(packets3.capture(), any())
                    assertThat(packets3.firstValue.wrap()).isIn(*expected3)
                    assertThat(packets3.secondValue.wrap()).isIn(*expected3)
                    assertThat(packets3.thirdValue.wrap()).isIn(*expected3)
                    assertThat(packets3.allValues[3].wrap()).isIn(*expected3)
                }
    }

}