// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import assertk.assert
import assertk.assertions.isEmpty
import net.postchain.common.BlockchainRid
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.base.*
import net.postchain.core.UserMistake
import net.postchain.network.util.peerInfoFromPublicKey
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultPeerCommunicationManager1PeerIT {

    private val cryptoSystem = SECP256K1CryptoSystem()
    private val blockchainRid = BlockchainRid.buildRepeat(0)

    private val privKey = cryptoSystem.getRandomBytes(32)
    private val pubKey = secp256k1_derivePubKey(privKey)

    private val privKey2 = cryptoSystem.getRandomBytes(32)
    private val pubKey2 = secp256k1_derivePubKey(privKey2)

    private val peerInfo = peerInfoFromPublicKey(pubKey)

    private fun startTestContext(peers: Array<PeerInfo>, pubKey: ByteArray): EbftIntegrationTestContext {
        val peerConfiguration = BasePeerCommConfiguration.build(
                peers, cryptoSystem, privKey, pubKey)

        return EbftIntegrationTestContext(peerConfiguration, blockchainRid)
    }

    @Test
    fun singlePeer_launched_successfully() {
        startTestContext(arrayOf(peerInfo), pubKey)
                .use { context ->
                    context.communicationManager.init()

                    // Waiting for all connections to be established
                    await().atMost(Duration.FIVE_SECONDS)
                            .untilAsserted {
                                val actual = context.connectionManager.getConnectedNodes(context.chainId)
                                assert(actual).isEmpty()
                            }
                }
    }

    @Test
    fun singlePeer_launching_with_empty_peers_will_result_in_exception() {
        assertThrows<UserMistake> {
            startTestContext(arrayOf(), pubKey)
                .use {
                    it.communicationManager.init()
                }
        }
    }

    @Test
    fun singlePeer_launching_with_wrong_pubkey_will_result_in_exception() {
        assertThrows<UserMistake> {
            startTestContext(arrayOf(peerInfo), pubKey2)
                .use {
                    it.communicationManager.init()
                }
        }
    }
}