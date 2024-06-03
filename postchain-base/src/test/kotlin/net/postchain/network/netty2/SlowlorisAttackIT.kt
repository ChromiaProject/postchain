// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.netty.channel.ChannelPipeline
import io.netty.handler.traffic.ChannelTrafficShapingHandler
import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.network.common.ConnectionDirection
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.util.peerInfoFromPublicKey
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.awaitility.Duration.FIVE_SECONDS
import org.awaitility.Duration.TEN_SECONDS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

class SlowlorisAttackIT {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val blockchainRid = BlockchainRid.ZERO_RID

    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo

    private lateinit var context1: EbftTestContext
    private lateinit var context2: EbftTestContext

    @BeforeEach
    fun setUp() {
        val keyPair1 = cryptoSystem.generateKeyPair()
        val keyPair2 = cryptoSystem.generateKeyPair()

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
        context1 = EbftTestContext(BasePeerCommConfiguration.build(peers, appConfig1), blockchainRid)
        context2 = EbftTestContext(BasePeerCommConfiguration.build(peers, appConfig2), blockchainRid)

        // Initializing
        context1.init()
        context2.init()
    }

    @AfterEach
    fun tearDown() {
        context1.shutdown()
        context2.shutdown()
    }

    /**
     * The malicious node opens 10 connections and starts sending handshake messages very slowly.
     * It can open as many such connections as it wants.
     */
    @Test
    fun `Slowloris attack succeeds`() {
        val peerDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)

        val slowlorisHandler: (ChannelPipeline) -> Unit = {
            it.addFirst(ChannelTrafficShapingHandler(1, 1))
        }

        // Disable ReadHandshakeTimeoutHandler for peer2
        context2.readHandshakeTimeoutHandler = false

        // Peer1 opens 10 connections with peer2
        repeat(10) {
            context1.connector.connectNode(
                    peerDescriptor2, peerInfo2, context1.buildPacketCodec(), postInitChannelHandler = slowlorisHandler)
        }

        // Asserting client connections
        await().atMost(TEN_SECONDS).untilAsserted {
            val connections = context1.connections
            assertThat(connections.size).isEqualTo(10)

            connections.map { (it as NettyClientPeerConnection<*>) }.forEach { connection ->
                assertThat(connection.isChannelActive()).isEqualTo(true)
                assertThat(connection.isConnected).isEqualTo(true)
                assertThat(connection.descriptor().nodeId).isEqualTo(peerInfo2.peerId())
            }
        }

        // Asserting server connections
        val handshakeTimeout = Duration(context1.connectionConfig.readHandshakeTimeout, TimeUnit.MILLISECONDS).plus(3_000)
        await().pollDelay(handshakeTimeout).atMost(Duration.ONE_MINUTE).untilAsserted {
            val serverConnections = context2.serverConnections
            assertThat(serverConnections.size).isEqualTo(10)

            serverConnections.map { (it as NettyServerPeerConnection<*>) }.forEach { connection ->
                // channel is active
                assertThat(connection.isChannelActive()).isEqualTo(true)

                // but handshake hasn't happened yet
                assertThat(connection.isConnected).isEqualTo(false)

                // and connection descriptor is null
                assertThrows<ProgrammerMistake>("Descriptor is null") {
                    connection.descriptor()
                }
            }

        }
    }

    /**
     * The malicious node opens 10 connections and starts sending handshake messages very slowly.
     * It can open as many such connections as it wants.
     *
     * The attack fails because the [ReadHandshakeTimeoutHandler] will close open connections that have not received
     * a handshake message within the read-handshake-timeout time interval.
     */
    @Test
    fun `Slowloris attack fails if ReadHandshakeTimeoutHandler is used`() {
        val peerConnectionDescriptor2 = PeerConnectionDescriptor(blockchainRid, peerInfo2.peerId(), ConnectionDirection.OUTGOING)

        val slowlorisHandler: (ChannelPipeline) -> Unit = {
            it.addFirst(ChannelTrafficShapingHandler(1, 1))
        }

        // Enable ReadHandshakeTimeoutHandler for peer2
        context2.readHandshakeTimeoutHandler = true

        // Peer1 opens 10 connections with peer2
        repeat(10) {
            context1.connector.connectNode(
                    peerConnectionDescriptor2, peerInfo2, context1.buildPacketCodec(), postInitChannelHandler = slowlorisHandler)
        }

        // Asserting client connections
        await().atMost(TEN_SECONDS).untilAsserted {
            val connections = context1.connections

            assertThat(connections.size).isEqualTo(10)

            connections.map { (it as NettyClientPeerConnection<*>) }.forEach { connection ->
                assertThat(connection.isChannelActive()).isEqualTo(true)
                assertThat(connection.isConnected).isEqualTo(true)
                assertThat(connection.descriptor().nodeId).isEqualTo(peerInfo2.peerId())
            }
        }

        // Asserting server connections
        //  - the first 10-second interval
        val handshakeTimeout = Duration(context1.connectionConfig.readHandshakeTimeout, TimeUnit.MILLISECONDS)
        await().atMost(handshakeTimeout).untilAsserted {
            val serverConnections = context2.serverConnections
            assertThat(serverConnections.size).isEqualTo(10)

            serverConnections.map { (it as NettyServerPeerConnection<*>) }.forEach { connection ->
                // channel is active
                assertThat(connection.isChannelActive()).isEqualTo(true)

                // but handshake hasn't happened yet
                assertThat(connection.isConnected).isEqualTo(false)

                // and connection descriptor is null
                assertThrows<ProgrammerMistake>("Descriptor is null") {
                    connection.descriptor()
                }
            }
        }

        //  - the second 10-second interval
        await().pollDelay(FIVE_SECONDS).atMost(Duration.ONE_MINUTE).untilAsserted {
            val serverConnections = context2.serverConnections
            assertThat(serverConnections.size).isEqualTo(10)

            serverConnections.map { (it as NettyServerPeerConnection<*>) }.forEach { connection ->
                // channel is inactive
                assertThat(connection.isChannelInactive()).isEqualTo(true)
            }
        }
    }
}