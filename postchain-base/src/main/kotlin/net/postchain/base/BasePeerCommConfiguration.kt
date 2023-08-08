// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.config.app.AppConfig
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Verifier

open class BasePeerCommConfiguration(
        override val networkNodes: NetworkNodes,
        private val cryptoSystem: CryptoSystem,
        private val privKey: ByteArray,
        override val pubKey: ByteArray
) : PeerCommConfiguration {

    companion object {
        // Used in tests only
        fun build(peers: Array<PeerInfo>,
                  appConfig: AppConfig
        ): BasePeerCommConfiguration {
            return build(peers.toSet(), appConfig)
        }

        fun build(peers: Collection<PeerInfo>,
                  appConfig: AppConfig
        ): BasePeerCommConfiguration {
            val nn = NetworkNodes.buildNetworkNodes(peers, appConfig)
            return BasePeerCommConfiguration(
                    nn,
                    appConfig.cryptoSystem,
                    appConfig.privKeyByteArray,
                    appConfig.pubKeyByteArray)
        }
    }


    override fun resolvePeer(peerID: ByteArray): PeerInfo? {
        return networkNodes[peerID]
    }

    override fun myPeerInfo(): PeerInfo = networkNodes.myself

    override fun sigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
    }

    override fun verifier(): Verifier = cryptoSystem.makeVerifier()
}