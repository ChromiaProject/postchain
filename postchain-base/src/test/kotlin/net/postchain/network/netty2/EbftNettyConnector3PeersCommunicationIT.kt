// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isIn
import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.ebft.message.GetBlockAtHeight
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class EbftNettyConnector3PeersCommunicationIT {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val blockchainRid = BlockchainRid.buildRepeat(0)

    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo
    private lateinit var peerInfo3: PeerInfo

    private lateinit var context1: EbftTestContext
    private lateinit var context2: EbftTestContext
    private lateinit var context3: EbftTestContext

    @BeforeEach
    fun setUp() {
        val keyPair1 = cryptoSystem.generateKeyPair()
        val keyPair2 = cryptoSystem.generateKeyPair()
        val keyPair3 = cryptoSystem.generateKeyPair()

        peerInfo1 = peerInfoFromPublicKey(keyPair1.pubKey.data)
        peerInfo2 = peerInfoFromPublicKey(keyPair2.pubKey.data)
        peerInfo3 = peerInfoFromPublicKey(keyPair3.pubKey.data)
        val peers = arrayOf(peerInfo1, peerInfo2, peerInfo3)

        val appConfig1: AppConfig = mock {
            on { cryptoSystem } doReturn cryptoSystem
            on { privKeyByteArray } doReturn keyPair1.privKey.data
            on { pubKeyByteArray } doReturn keyPair1.pubKey.data
        }
        val appConfig2: AppConfig = mock {
            on { cryptoSystem } doReturn cryptoSystem
            on { privKeyByteArray } doReturn keyPair2.privKey.data
            on { pubKeyByteArray } doReturn keyPair2.pubKey.data
        }
        val appConfig3: AppConfig = mock {
            on { cryptoSystem } doReturn cryptoSystem
            on { privKeyByteArray } doReturn keyPair3.privKey.data
            on { pubKeyByteArray } doReturn keyPair3.pubKey.data
        }

        // Creating
        context1 = EbftTestContext(BasePeerCommConfiguration.build(peers, appConfig1), blockchainRid)
        context2 = EbftTestContext(BasePeerCommConfiguration.build(peers, appConfig2), blockchainRid)
        context3 = EbftTestContext(BasePeerCommConfiguration.build(peers, appConfig3), blockchainRid)

        // Initializing
        context1.init()
        context2.init()
        context3.init()
    }

    @AfterEach
    fun tearDown() {
        context1.shutdown()
        context2.shutdown()
        context3.shutdown()
    }

    @Test
    fun threePeers_ConnectAndCommunicate_Successfully() {
        // Connecting
        // * 1 -> 2
        val peerDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor2, peerInfo2, context1.buildPacketEncoder(), context1.buildPacketDecoder())
        // * 1 -> 3
        val peerDescriptor3 = PeerConnectionDescriptor(blockchainRid, peerInfo3.peerId(), ConnectionDirection.OUTGOING)
        context1.peer.connectNode(peerDescriptor3, peerInfo3, context2.buildPacketEncoder(), context2.buildPacketDecoder())
        // * 3 -> 2
        context3.peer.connectNode(peerDescriptor2, peerInfo2, context3.buildPacketEncoder(), context3.buildPacketDecoder())

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
        // * 1 -> 2
        val packets1 = arrayOf(
                GetBlockAtHeight(10),
                GetBlockAtHeight(11))
        connection1.firstValue.sendPacket(lazy { context1.encodePacket(packets1[0], 1) })
        connection1.firstValue.sendPacket(lazy { context1.encodePacket(packets1[1], 1) })
        // * 1 -> 3
        connection1.secondValue.sendPacket(lazy { context1.encodePacket(packets1[0], 1) })
        connection1.secondValue.sendPacket(lazy { context1.encodePacket(packets1[1], 1) })

        // * 2 -> 1
        val packets2 = arrayOf(
                GetBlockAtHeight(20),
                GetBlockAtHeight(21))
        connection2.firstValue.sendPacket(lazy { context2.encodePacket(packets2[0], 1) })
        connection2.firstValue.sendPacket(lazy { context2.encodePacket(packets2[1], 1) })
        // * 2 -> 3
        connection2.secondValue.sendPacket(lazy { context2.encodePacket(packets2[0], 1) })
        connection2.secondValue.sendPacket(lazy { context2.encodePacket(packets2[1], 1) })

        // * 3 -> 1
        val packets3 = arrayOf(
                GetBlockAtHeight(30),
                GetBlockAtHeight(31))
        connection3.firstValue.sendPacket(lazy { context3.encodePacket(packets3[0], 1) })
        connection3.firstValue.sendPacket(lazy { context3.encodePacket(packets3[1], 1) })
        // * 3 -> 2
        connection3.secondValue.sendPacket(lazy { context3.encodePacket(packets3[0], 1) })
        connection3.secondValue.sendPacket(lazy { context3.encodePacket(packets3[1], 1) })

        // * asserting
        await().atMost(TEN_SECONDS)
                .untilAsserted {
                    // Peer1
                    val actualPackets1 = argumentCaptor<ByteArray>()
                    val expected1 = arrayOf(20L, 21L, 30L, 31L)
                    verify(context1.packets, times(4)).handle(actualPackets1.capture(), any())
                    actualPackets1.allValues
                            .map { (context1.decodePacket(it, 1) as GetBlockAtHeight).height }
                            .forEach { assertThat(it).isIn(*expected1) }

                    // Peer2
                    val actualPackets2 = argumentCaptor<ByteArray>()
                    val expected2 = arrayOf(10L, 11L, 30L, 31L)
                    verify(context2.packets, times(4)).handle(actualPackets2.capture(), any())
                    actualPackets2.allValues
                            .map { (context2.decodePacket(it, 1) as GetBlockAtHeight).height }
                            .forEach { assertThat(it).isIn(*expected2) }

                    // Peer2
                    val actualPackets3 = argumentCaptor<ByteArray>()
                    val expected3 = arrayOf(10L, 11L, 20L, 21L)
                    verify(context3.packets, times(4)).handle(actualPackets3.capture(), any())
                    actualPackets3.allValues
                            .map { (context2.decodePacket(it, 1) as GetBlockAtHeight).height }
                            .forEach { assertThat(it).isIn(*expected3) }
                }
    }
}