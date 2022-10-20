// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.CryptoSystem
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
                  cryptoSystem: CryptoSystem,
                  privKey: ByteArray,
                  pubKey: ByteArray
        ): BasePeerCommConfiguration {
            return build(peers.toSet(), cryptoSystem, privKey, pubKey)
        }

        fun build(peers: Collection<PeerInfo>,
                  cryptoSystem: CryptoSystem,
                  privKey: ByteArray,
                  pubKey: ByteArray
        ): BasePeerCommConfiguration {
            val nn = NetworkNodes.buildNetworkNodes(peers, WrappedByteArray(pubKey))
            return BasePeerCommConfiguration(nn, cryptoSystem, privKey, pubKey)
        }
    }


    override fun resolvePeer(peerID: ByteArray): PeerInfo? {
        return networkNodes[peerID]
    }

    override fun myPeerInfo(): PeerInfo = networkNodes.myself

    override fun sigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(pubKey, privKey)
    }

    override fun verifier(): Verifier = cryptoSystem.makeVerifier()
}