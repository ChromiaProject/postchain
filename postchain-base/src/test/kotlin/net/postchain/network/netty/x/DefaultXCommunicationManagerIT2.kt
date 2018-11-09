package net.postchain.network.x

import com.nhaarman.mockitokotlin2.*
import net.postchain.base.PeerInfo
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.secp256k1_derivePubKey
import net.postchain.network.IdentPacketInfo
import net.postchain.network.PacketConverter
import net.postchain.network.netty.NettyConnectorFactory
import net.postchain.network.netty.NettyIO
import org.awaitility.Awaitility.await
import org.awaitility.Duration.FIVE_SECONDS
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DefaultXCommunicationManagerIT2 {

    private val blockchainRid = byteArrayOf(0x01)

    private lateinit var peerInfo1: PeerInfo
    private lateinit var identPacket1: ByteArray

    private lateinit var peerInfo2: PeerInfo
    private lateinit var identPacket2: ByteArray

    private lateinit var peerInfo3: PeerInfo
    private lateinit var identPacket3: ByteArray

    private val privateKey = "3132333435363738393031323334353637383930313233343536373839303131".toByteArray()
    private val privateKey2 = "3132333435363738393031323334353637383930313233343536373839303132".toByteArray()
    private val privateKey3 = "3132333435363738393031323334353637383930313233343536373839303133".toByteArray()
    private val publicKey = secp256k1_derivePubKey(privateKey)
    private val publicKey2 = secp256k1_derivePubKey(privateKey2)
    private val publicKey3 = secp256k1_derivePubKey(privateKey3)

    private val ephemeralKey = SECP256K1CryptoSystem().getRandomBytes(NettyIO.keySizeBytes)
    private val ephemeralPubKey = secp256k1_derivePubKey(ephemeralKey)

    @Before
    fun setUp() {
        // TODO: [et]: Make dynamic ports
        peerInfo1 = PeerInfo("localhost", 3331, publicKey, privateKey)
        identPacket1 = byteArrayOf(0x01, 0x01)

        peerInfo2 = PeerInfo("localhost", 3332, publicKey2, privateKey2)
        identPacket2 = byteArrayOf(0x02, 0x02)

        peerInfo3= PeerInfo("localhost", 3333, publicKey3, privateKey3)
        identPacket3 = byteArrayOf(0x03, 0x03)
    }

    @Test
    fun twoPeers_SendsPackets_Successfully() {
        val connectorFactory = NettyConnectorFactory()
        val peerInfos = arrayOf(peerInfo1, peerInfo2)

        // Given
        val packetConverter1: PacketConverter<Int> = mock {
            on { makeIdentPacket(any()) } doReturn identPacket2
            on { parseIdentPacket(any()) } doReturn IdentPacketInfo(peerInfo1.pubKey, blockchainRid, ephemeralPubKey, ephemeralPubKey)

            on { encodePacket(2) } doReturn byteArrayOf(0x02)
            on { encodePacket(22) } doReturn byteArrayOf(0x02, 0x02)
            on { encodePacket(222) } doReturn byteArrayOf(0x02, 0x02, 0x02)

            onGeneric { decodePacket(peerInfo1.pubKey, byteArrayOf(0x01)) } doReturn 1
            onGeneric { decodePacket(peerInfo1.pubKey, byteArrayOf(0x01, 0x01)) } doReturn 11
        }

        val packetConverter2: PacketConverter<Int> = mock {
            on { makeIdentPacket(any()) } doReturn identPacket1
            on { parseIdentPacket(any()) } doReturn IdentPacketInfo(peerInfo2.pubKey, blockchainRid, ephemeralPubKey, ephemeralPubKey)

            onGeneric { decodePacket(peerInfo2.pubKey, byteArrayOf(0x02)) } doReturn 2
            onGeneric { decodePacket(peerInfo2.pubKey, byteArrayOf(0x02, 0x02)) } doReturn 22
            onGeneric { decodePacket(peerInfo2.pubKey, byteArrayOf(0x02, 0x02, 0x02)) } doReturn 222

            on { encodePacket(1) } doReturn byteArrayOf(0x01)
            on { encodePacket(11) } doReturn byteArrayOf(0x01, 0x01)
        }

        val context1 = IntegrationTestContext(connectorFactory, blockchainRid, peerInfos, 0, packetConverter1)
        val context2 = IntegrationTestContext(connectorFactory, blockchainRid, peerInfos, 1, packetConverter2)

        // TODO: [et]: Fix two-connected-nodes problem
        await().atMost(FIVE_SECONDS)
                .untilCallTo { context1.communicationManager.connectionManager.getConnectedPeers(1L) }
                .matches { peers -> !peers!!.isEmpty() }

        await().atMost(FIVE_SECONDS)
                .untilCallTo { context2.communicationManager.connectionManager.getConnectedPeers(1L) }
                .matches { peers -> !peers!!.isEmpty() }

        // Interactions
        context1.communicationManager.sendPacket(2, setOf(1))

        context2.communicationManager.sendPacket(1, setOf(0))
        context2.communicationManager.sendPacket(11, setOf(0))

        context1.communicationManager.sendPacket(22, setOf(1))
        context1.communicationManager.sendPacket(222, setOf(1))

        // Waiting for all transfers
        TimeUnit.SECONDS.sleep(3)

        // Then
        // - peer1
//        verify(packetConverter1).makeIdentPacket(any())
  //      verify(packetConverter1).parseIdentPacket(any())

        verify(packetConverter1).encodePacket(2)
        verify(packetConverter1).encodePacket(22)
        verify(packetConverter1).encodePacket(222)
        verify(packetConverter2).encodePacket(1)
        verify(packetConverter2).encodePacket(11)


        verify(packetConverter1).decodePacket(any(), eq(byteArrayOf(0x01)))
        verify(packetConverter1).decodePacket(any(), eq(byteArrayOf(0x01, 0x01)))
        verify(packetConverter2).decodePacket(any(), eq(byteArrayOf(0x02)))
        verify(packetConverter2).decodePacket(any(), eq(byteArrayOf(0x02, 0x02)))
        verify(packetConverter2).decodePacket(any(), eq(byteArrayOf(0x02, 0x02, 0x02)))

//
//        // - peer2
//        verify(packetConverter2).makeIdentPacket(any())
//        verify(packetConverter2).parseIdentPacket(any())
//
//
//
//        // - Received packets
//        // -- peer2
        await().atMost(FIVE_SECONDS).untilAsserted {
            Assert.assertArrayEquals(
                    arrayOf(2, 22, 222),
                    context2.communicationManager.getPackets().map { it.second }.toTypedArray()
            )
        }

        // -- peer1
        await().atMost(FIVE_SECONDS).untilAsserted {
            Assert.assertArrayEquals(
                    arrayOf(1, 11),
                    context1.communicationManager.getPackets().map { it.second }.toTypedArray()
            )
        }
    }

    @Test
    fun threePeers_SendsPackets_Successfully() {
        val connectorFactory = NettyConnectorFactory()
        val peerInfos = arrayOf(peerInfo1, peerInfo2, peerInfo3)

        // Given
        val packetConverter1: PacketConverter<Int> = mock {
            on { makeIdentPacket(any()) } doReturn identPacket2
            on { parseIdentPacket(any()) } doReturn IdentPacketInfo(peerInfo1.pubKey, blockchainRid, ephemeralPubKey, ephemeralPubKey)

            on { encodePacket(2) } doReturn byteArrayOf(0x02)
            on { encodePacket(22) } doReturn byteArrayOf(0x02, 0x02)
            on { encodePacket(222) } doReturn byteArrayOf(0x02, 0x02, 0x02)
            on { encodePacket(3) } doReturn byteArrayOf(0x03)

            onGeneric { decodePacket(peerInfo1.pubKey, byteArrayOf(0x01)) } doReturn 1
            onGeneric { decodePacket(peerInfo1.pubKey, byteArrayOf(0x01, 0x01)) } doReturn 11
        }

        val packetConverter2: PacketConverter<Int> = mock {
            on { makeIdentPacket(any()) } doReturn identPacket1
            on { parseIdentPacket(any()) } doReturn IdentPacketInfo(peerInfo2.pubKey, blockchainRid, ephemeralPubKey, ephemeralPubKey)

            onGeneric { decodePacket(peerInfo2.pubKey, byteArrayOf(0x02)) } doReturn 2
            onGeneric { decodePacket(peerInfo2.pubKey, byteArrayOf(0x02, 0x02)) } doReturn 22
            onGeneric { decodePacket(peerInfo2.pubKey, byteArrayOf(0x02, 0x02, 0x02)) } doReturn 222

            on { encodePacket(1) } doReturn byteArrayOf(0x01)
            on { encodePacket(11) } doReturn byteArrayOf(0x01, 0x01)
        }

        val packetConverter3: PacketConverter<Int> = mock {
            on { makeIdentPacket(any()) } doReturn identPacket1
            on { parseIdentPacket(any()) } doReturn IdentPacketInfo(peerInfo3.pubKey, blockchainRid, ephemeralPubKey, ephemeralPubKey)

            onGeneric { decodePacket(peerInfo3.pubKey, byteArrayOf(0x03)) } doReturn 3

        }

        val context1 = IntegrationTestContext(connectorFactory, blockchainRid, peerInfos, 0, packetConverter1)
        val context2 = IntegrationTestContext(connectorFactory, blockchainRid, peerInfos, 1, packetConverter2)
        val context3 = IntegrationTestContext(connectorFactory, blockchainRid, peerInfos, 2, packetConverter3)

        // TODO: [et]: Fix two-connected-nodes problem
        await().atMost(FIVE_SECONDS)
                .untilCallTo { context1.communicationManager.connectionManager.getConnectedPeers(1L) }
                .matches { peers -> !peers!!.isEmpty() }

        await().atMost(FIVE_SECONDS)
                .untilCallTo { context2.communicationManager.connectionManager.getConnectedPeers(1L) }
                .matches { peers -> !peers!!.isEmpty() }

//        await().atMost(FIVE_SECONDS)
//                .untilCallTo { context3.communicationManager.connectionManager.getConnectedPeers(1L) }
//                .matches { peers -> !peers!!.isEmpty() }

        // Interactions
        context1.communicationManager.sendPacket(2, setOf(1))
        context1.communicationManager.sendPacket(3, setOf(2))

        context2.communicationManager.sendPacket(1, setOf(0))
        context2.communicationManager.sendPacket(11, setOf(0))

        context1.communicationManager.sendPacket(22, setOf(1))
        context1.communicationManager.sendPacket(222, setOf(1))

        // Waiting for all transfers
        TimeUnit.SECONDS.sleep(5)

        // Then
        // - peer1
//        verify(packetConverter1).makeIdentPacket(any())
        //      verify(packetConverter1).parseIdentPacket(any())

        verify(packetConverter1).encodePacket(2)
        verify(packetConverter1).encodePacket(22)
        verify(packetConverter1).encodePacket(222)
        verify(packetConverter1).encodePacket(3)
        verify(packetConverter2).encodePacket(1)
        verify(packetConverter2).encodePacket(11)


//        verify(packetConverter1).decodePacket(any(), eq(byteArrayOf(0x01)))
//        verify(packetConverter1).decodePacket(any(), eq(byteArrayOf(0x01, 0x01)))
//        verify(packetConverter2).decodePacket(any(), eq(byteArrayOf(0x02)))
//        verify(packetConverter2).decodePacket(any(), eq(byteArrayOf(0x02, 0x02)))
//        verify(packetConverter2).decodePacket(any(), eq(byteArrayOf(0x02, 0x02, 0x02)))
//        verify(packetConverter3).decodePacket(any(), eq(byteArrayOf(0x03)))

//
//        // - peer2
//        verify(packetConverter2).makeIdentPacket(any())
//        verify(packetConverter2).parseIdentPacket(any())
//
//
//
//        // - Received packets
//        // -- peer2

        await().atMost(FIVE_SECONDS).untilAsserted {
            Assert.assertArrayEquals(
                    arrayOf(3),
                    context3.communicationManager.getPackets().map { it.second }.toTypedArray()
            )
        }

        await().atMost(FIVE_SECONDS).untilAsserted {
            Assert.assertArrayEquals(
                    arrayOf(2, 22, 222),
                    context2.communicationManager.getPackets().map { it.second }.toTypedArray()
            )
        }

        // -- peer1
        await().atMost(FIVE_SECONDS).untilAsserted {
            Assert.assertArrayEquals(
                    arrayOf(1, 11),
                    context1.communicationManager.getPackets().map { it.second }.toTypedArray()
            )
        }
    }
}