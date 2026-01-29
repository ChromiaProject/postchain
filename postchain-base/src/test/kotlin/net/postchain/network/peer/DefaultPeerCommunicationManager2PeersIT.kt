// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.ebft.EBFT_VERSION
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.network.util.peerInfoFromPublicKey
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DefaultPeerCommunicationManager2PeersIT {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val blockchainRid = BlockchainRid.buildRepeat(0)

    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo

    private lateinit var context1: EbftIntegrationTestContext
    private lateinit var context2: EbftIntegrationTestContext

    private val keyPair1 = cryptoSystem.generateKeyPair()
    private val keyPair2 = cryptoSystem.generateKeyPair()

    @BeforeEach
    fun setUp() {
        peerInfo1 = peerInfoFromPublicKey(keyPair1.pubKey.data)
        peerInfo2 = peerInfoFromPublicKey(keyPair2.pubKey.data)
        val peers = arrayOf(peerInfo1, peerInfo2)
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

        // Creating
        context1 = EbftIntegrationTestContext(
                BasePeerCommConfiguration.build(peers, appConfig1),
                blockchainRid)

        context2 = EbftIntegrationTestContext(
                BasePeerCommConfiguration.build(peers, appConfig2),
                blockchainRid)

        // Initializing
        context1.communicationManager.init()
        context2.communicationManager.init()
    }

    @AfterEach
    fun tearDown() {
        context1.shutdown()
        context2.shutdown()
    }

    @Test
    fun twoPeers_SendsPackets_Successfully() {
        // Waiting for all connections to be established
        await().atMost(Duration.FIVE_SECONDS)
                .untilAsserted {
                    val actual1 = context1.connectionManager.getConnectedNodes(context1.chainId)
                    assertThat(actual1).containsExactly(peerInfo2.pubKey.wrap())

                    val actual2 = context2.connectionManager.getConnectedNodes(context2.chainId)
                    assertThat(actual2).containsExactly(peerInfo1.pubKey.wrap())
                }

        // Sending packets
        // * 1 -> 2
        val packets1 = arrayOf(
                GetBlockAtHeight(10),
                GetBlockAtHeight(11))
        context1.communicationManager.sendPacket(packets1[0], NodeRid(keyPair2.pubKey.data))
        context1.communicationManager.sendPacket(packets1[1], NodeRid(keyPair2.pubKey.data))
        // * 2 -> 1
        val packets2 = arrayOf(
                GetBlockAtHeight(20),
                GetBlockAtHeight(21),
                GetBlockAtHeight(22))
        context2.communicationManager.sendPacket(packets2[0], NodeRid(keyPair1.pubKey.data))
        context2.communicationManager.sendPacket(packets2[1], NodeRid(keyPair1.pubKey.data))
        context2.communicationManager.sendPacket(packets2[2], NodeRid(keyPair1.pubKey.data))

        // * asserting
        val actual1 = mutableListOf<Long>()
        val actual2 = mutableListOf<Long>()
        await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    // Peer1
                    val actualPackets1 = context1.communicationManager.getPackets()
                    actual1.addAll(actualPackets1.filter { it.message is GetBlockAtHeight }.map { (it.message as GetBlockAtHeight).height })
                    assertThat(actual1).containsExactly(20L, 21L, 22L)

                    // Peer2
                    val actualPackets2 = context2.communicationManager.getPackets()
                    actual2.addAll(actualPackets2.filter { it.message is GetBlockAtHeight }.map { (it.message as GetBlockAtHeight).height })
                    assertThat(actual2).containsExactly(10L, 11L)
                }
    }

    @Test
    fun `send message which is unrecognized by receiver`() {
        val message = GtvEncoder.encodeGtv(gtv(gtv(4711), gtv("bogus")))
        val signature = cryptoSystem.buildSigMaker(keyPair1).signMessage(message)
        sendStrangeMessage(GtvEncoder.encodeGtv(gtv(gtv(message), gtv(signature.subjectID), gtv(signature.data))))
    }

    @Test
    fun `send message with bad signature`() {
        val message = GetBlockAtHeight(0).encoded(EBFT_VERSION).value
        val signature = cryptoSystem.buildSigMaker(keyPair1).signMessage("ABCD".hexStringToByteArray())
        sendStrangeMessage(GtvEncoder.encodeGtv(gtv(gtv(message), gtv(signature.subjectID), gtv(signature.data))))
    }

    @Test
    fun `send garbage inner message`() {
        val message = "ABCD".hexStringToByteArray()
        val signature = cryptoSystem.buildSigMaker(keyPair1).signMessage(message)
        sendStrangeMessage(GtvEncoder.encodeGtv(gtv(gtv(message), gtv(signature.subjectID), gtv(signature.data))))
    }

    @Test
    fun `send incorrectly structured inner message`() {
        val message = GtvEncoder.encodeGtv(gtv(mapOf("foo" to gtv(17))))
        val signature = cryptoSystem.buildSigMaker(keyPair1).signMessage(message)
        sendStrangeMessage(GtvEncoder.encodeGtv(gtv(gtv(message), gtv(signature.subjectID), gtv(signature.data))))
    }

    @Test
    fun `send garbage outer message`() {
        val message = "ABCD".hexStringToByteArray()
        sendStrangeMessage(message)
    }

    private fun sendStrangeMessage(message: ByteArray) {
        // Waiting for all connections to be established
        await().atMost(Duration.FIVE_SECONDS)
                .untilAsserted {
                    val actual1 = context1.connectionManager.getConnectedNodes(context1.chainId)
                    assertThat(actual1).containsExactly(peerInfo2.pubKey.wrap())

                    val actual2 = context2.connectionManager.getConnectedNodes(context2.chainId)
                    assertThat(actual2).containsExactly(peerInfo1.pubKey.wrap())
                }

        // Consume version packet
        await().atMost(Duration.FIVE_SECONDS)
                .untilAsserted {
                    val packets = context2.communicationManager.getPackets()
                    assertThat(packets).hasSize(1)
                    assertThat(packets.first().message).isInstanceOf(EbftVersion::class.java)
                }

        context1.connectionManager.sendPacket(lazy { message }, context1.chainId, NodeRid(keyPair2.pubKey.data))

        Thread.sleep(1 * 1000)
        assertThat(context2.communicationManager.getPackets()).isEmpty()

        // Ensure that valid messages are still received
        context1.communicationManager.sendPacket(GetBlockAtHeight(1), NodeRid(keyPair2.pubKey.data))
        await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            val actual = context2.communicationManager.getPackets()
            assertThat(actual).hasSize(1)
            assertThat(actual.first().message).isInstanceOf(GetBlockAtHeight::class.java)
        }
    }
}