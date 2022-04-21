// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Key
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Verifier

open class BasePeerCommConfiguration(
        override val networkNodes: NetworkNodes,
        private val cryptoSystem: CryptoSystem,
        private val privKey: Key,
        override val pubKey: Key
) : PeerCommConfiguration {

    companion object {
        // Used in tests only
        fun build(peers: Array<PeerInfo>,
                  cryptoSystem: CryptoSystem,
                  privKey: Key,
                  pubKey: Key
        ): BasePeerCommConfiguration {
            return build(peers.toSet(), cryptoSystem, privKey, pubKey)
        }

        fun build(peers: Collection<PeerInfo>,
                  cryptoSystem: CryptoSystem,
                  privKey: Key,
                  pubKey: Key
        ): BasePeerCommConfiguration {
            val nn = NetworkNodes.buildNetworkNodes(peers, pubKey)
            return BasePeerCommConfiguration(nn, cryptoSystem, privKey, pubKey)
        }
    }


    override fun resolvePeer(peerID: ByteArray): PeerInfo? {
        return networkNodes[peerID]
    }

    override fun myPeerInfo(): PeerInfo = networkNodes.myself

    override fun sigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(pubKey.byteArray, privKey.byteArray)
    }

    override fun verifier(): Verifier = cryptoSystem.makeVerifier()
}