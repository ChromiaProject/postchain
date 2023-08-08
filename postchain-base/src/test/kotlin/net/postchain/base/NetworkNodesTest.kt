package net.postchain.base

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class NetworkNodesTest {

    private lateinit var peerInfo1: PeerInfo
    private lateinit var peerInfo2: PeerInfo

    companion object {
        private const val PORT_1 = 1
        private const val PORT_2 = 2
        private const val PORT_3 = 3

        private val PUBKEY_1 = byteArrayOf(0x01)
        private val PUBKEY_2 = byteArrayOf(0x02)
    }

    @BeforeEach
    fun setUp() {
        peerInfo1 = PeerInfo("", PORT_1, PUBKEY_1)
        peerInfo2 = PeerInfo("", PORT_2, PUBKEY_2)
    }

    @Test
    fun `No peers should throw exception`() {
        assertThrows<UserMistake> {
            NetworkNodes.buildNetworkNodes(emptySet(), mock())
        }
    }

    @Test
    fun `Missing 'me' should throw exception`() {
        // setup
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn PUBKEY_1
        }
        // execute & verify
        assertThrows<UserMistake> {
            NetworkNodes.buildNetworkNodes(setOf(peerInfo2), appConfig)
        }
    }

    @Test
    fun `Myself should be setup`() {
        // setup
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn PUBKEY_1
        }
        // execute
        val nodes = NetworkNodes.buildNetworkNodes(setOf(peerInfo1), appConfig)
        // verify
        assertThat(nodes.myself.port).isEqualTo(PORT_1)
        assertThat(nodes.hasPeers()).isFalse()
    }

    @Test
    fun `Port present in app config should take precedence`() {
        // setup
        val appConfig: AppConfig = mock {
            on { hasPort } doReturn true
            on { port } doReturn PORT_3
            on { pubKeyByteArray } doReturn PUBKEY_1
        }
        // execute
        val nodes = NetworkNodes.buildNetworkNodes(setOf(peerInfo1), appConfig)
        // verify
        assertThat(nodes.myself.port).isEqualTo(PORT_3)
    }

    @Test
    fun `Multiple peers should be handled`() {
        // setup
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn PUBKEY_1
        }
        // execute
        val nodes = NetworkNodes.buildNetworkNodes(setOf(peerInfo1, peerInfo2), appConfig)
        // verify
        assertThat(nodes.hasPeers()).isTrue()
        assertThat(nodes.myself).isEqualTo(peerInfo1)
        assertThat(nodes[PUBKEY_1]).isNull()
        assertThat(nodes[PUBKEY_2]).isEqualTo(peerInfo2)
    }
}