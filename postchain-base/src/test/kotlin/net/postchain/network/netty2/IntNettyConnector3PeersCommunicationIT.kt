// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import assertk.assertThat
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class IntNettyConnector3PeersCommunicationIT {

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
                    it.peer.init(peerInfo, it.packetDecoder)
                }
    }

    private fun stopContext(context: IntTestContext) {
        context.shutdown()
    }

    @Test
    fun testConnectAndCommunicate() {
        // Connecting
        // * 1 -> 2
        val peerDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor2, peerInfo2, context1.packetEncoder)
        // * 1 -> 3
        val peerDescriptor3 = PeerConnectionDescriptor(blockchainRid, peerInfo3.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor3, peerInfo3, context1.packetEncoder)
        // * 3 -> 2
        context3.peer.connectNode(peerDescriptor2, peerInfo2, context3.packetEncoder)

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

        // Sending packets
        // * 1 -> 2 and 1 -> 3
        val packet1 = byteArrayOf(10, 2, 3, 4)
        connection1.firstValue.sendPacket { packet1 }
        connection1.secondValue.sendPacket { packet1 }
        // * 2 -> 1 and 2 -> 3
        val packet2 = byteArrayOf(1, 20, 3, 4)
        connection2.firstValue.sendPacket { packet2 }
        connection2.secondValue.sendPacket { packet2 }
        // * 3 -> 1 and 3 -> 2
        val packet3 = byteArrayOf(1, 2, 30, 4)
        connection3.firstValue.sendPacket { packet3 }
        connection3.secondValue.sendPacket { packet3 }

        // * asserting
        await().atMost(TEN_SECONDS)
                .untilAsserted {
                    // Peer1
                    val packets1 = argumentCaptor<ByteArray>()
                    val expected1 = arrayOf(packet2, packet3).map(ByteArray::wrap).toTypedArray()
                    verify(context1.packets, times(2)).handle(packets1.capture(), any())
                    assertThat(packets1.firstValue.wrap()).isIn(*expected1)
                    assertThat(packets1.secondValue.wrap()).isIn(*expected1)

                    // Peer2
                    val packets2 = argumentCaptor<ByteArray>()
                    val expected2 = arrayOf(packet1, packet3).map(ByteArray::wrap).toTypedArray()
                    verify(context2.packets, times(2)).handle(packets2.capture(), any())
                    assertThat(packets2.firstValue.wrap()).isIn(*expected2)
                    assertThat(packets2.secondValue.wrap()).isIn(*expected2)

                    // Peer3
                    val packets3 = argumentCaptor<ByteArray>()
                    val expected3 = arrayOf(packet1, packet2).map(ByteArray::wrap).toTypedArray()
                    verify(context3.packets, times(2)).handle(packets3.capture(), any())
                    assertThat(packets3.firstValue.wrap()).isIn(*expected3)
                    assertThat(packets3.secondValue.wrap()).isIn(*expected3)
                }
    }

}