// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import assertk.assertThat
import assertk.assertions.isEmpty
import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.network.util.peerInfoFromPublicKey
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DefaultPeerCommunicationManager1PeerIT {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val blockchainRid = BlockchainRid.buildRepeat(0)

    private val keyPair = cryptoSystem.generateKeyPair()
    private val keyPair2 = cryptoSystem.generateKeyPair()

    private val peerInfo = peerInfoFromPublicKey(keyPair.pubKey.data)

    private fun startTestContext(peers: Array<PeerInfo>, pubKey: ByteArray): EbftIntegrationTestContext {
        val appConfig: AppConfig = mock {
            on { cryptoSystem } doReturn cryptoSystem
            on { privKeyByteArray } doReturn keyPair.privKey.data
            on { pubKeyByteArray } doReturn pubKey
        }
        val peerConfiguration = BasePeerCommConfiguration.build(peers, appConfig)

        return EbftIntegrationTestContext(peerConfiguration, blockchainRid)
    }

    @Test
    fun singlePeer_launched_successfully() {
        startTestContext(arrayOf(peerInfo), keyPair.pubKey.data)
                .use { context ->
                    context.communicationManager.init()

                    // Waiting for all connections to be established
                    await().atMost(Duration.FIVE_SECONDS)
                            .untilAsserted {
                                val actual = context.connectionManager.getConnectedNodes(context.chainId)
                                assertThat(actual).isEmpty()
                            }
                }
    }

    @Test
    fun singlePeer_launching_with_empty_peers_will_result_in_exception() {
        assertThrows<UserMistake> {
            startTestContext(arrayOf(), keyPair.pubKey.data)
                    .use {
                        it.communicationManager.init()
                    }
        }
    }

    @Test
    fun singlePeer_launching_with_wrong_pubkey_will_result_in_exception() {
        assertThrows<UserMistake> {
            startTestContext(arrayOf(peerInfo), keyPair2.pubKey.data)
                    .use {
                        it.communicationManager.init()
                    }
        }
    }
}
