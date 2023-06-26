// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isIn
import assertk.isContentEqualTo
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

class IntNettyConnector2PeersCommunicationIT {

    private val blockchainRid = BlockchainRid.buildRepeat(0x01)
    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo
    private lateinit var context1: IntTestContext
    private lateinit var context2: IntTestContext

    @BeforeEach
    fun setUp() {
        peerInfo1 = peerInfoFromPublicKey(byteArrayOf(0, 0, 0, 1))
        peerInfo2 = peerInfoFromPublicKey(byteArrayOf(0, 0, 0, 2))

        // Creating
        context1 = IntTestContext(peerInfo1, arrayOf(peerInfo1, peerInfo2))
        context2 = IntTestContext(peerInfo2, arrayOf(peerInfo1, peerInfo2))

        // Initializing
        context1.peer.init(peerInfo1, context1.packetDecoder)
        context2.peer.init(peerInfo2, context2.packetDecoder)
    }

    @AfterEach
    fun tearDown() {
        context1.peer.shutdown()
        context2.peer.shutdown()
    }

    @Test
    fun testConnectAndCommunicate() {
        // Connecting 1 -> 2
        val peerDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor2, peerInfo2, context1.packetEncoder)

        // Waiting for all connections to be established
        val connection1 = argumentCaptor<PeerConnection>()
        val connection2 = argumentCaptor<PeerConnection>()
        await().atMost(FIVE_SECONDS)
                .untilAsserted {
                    verify(context1.events).onNodeConnected(connection1.capture())
                    assertThat(connection1.firstValue.descriptor().nodeId.data).isContentEqualTo(peerInfo2.pubKey)

                    verify(context2.events).onNodeConnected(connection2.capture())
                    assertThat(connection2.firstValue.descriptor().nodeId.data).isContentEqualTo(peerInfo1.pubKey)
                }

        // Sending packets
        // * 1 -> 2
        val packets1 = arrayOf(
                byteArrayOf(1, 2, 3, 4),
                byteArrayOf(10, 2, 3, 4),
                byteArrayOf(100, 2, 3, 4))
        connection1.firstValue.sendPacket { packets1[0] }
        connection1.firstValue.sendPacket { packets1[1] }
        connection1.firstValue.sendPacket { packets1[2] }
        // * 2 -> 1
        val packets2 = arrayOf(
                byteArrayOf(1, 2, 3, 4),
                byteArrayOf(10, 20, 30, 40))
        connection2.firstValue.sendPacket { packets2[0] }
        connection2.firstValue.sendPacket { packets2[1] }

        // * asserting
        await().atMost(TEN_SECONDS)
                .untilAsserted {
                    // Peer1
                    val actualPackets1 = argumentCaptor<ByteArray>()
                    val expected1 = packets2.map(ByteArray::wrap).toTypedArray()
                    verify(context1.packets, times(2)).handle(actualPackets1.capture(), any())
                    assertThat(actualPackets1.firstValue.wrap()).isIn(*expected1)
                    assertThat(actualPackets1.secondValue.wrap()).isIn(*expected1)

                    // Peer2
                    val actualPackets2 = argumentCaptor<ByteArray>()
                    val expected2 = packets1.map(ByteArray::wrap).toTypedArray()
                    verify(context2.packets, times(3)).handle(actualPackets2.capture(), any())
                    assertThat(actualPackets2.firstValue.wrap()).isIn(*expected2)
                    assertThat(actualPackets2.secondValue.wrap()).isIn(*expected2)
                    assertThat(actualPackets2.thirdValue.wrap()).isIn(*expected2)
                }
    }
}