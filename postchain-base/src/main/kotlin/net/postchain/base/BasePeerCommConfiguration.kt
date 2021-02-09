// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.core.ByteArrayKey

class BasePeerCommConfiguration(override val networkNodes: NetworkNodes,
                                private val cryptoSystem: CryptoSystem,
                                private val privKey: ByteArray,
                                override val pubKey: ByteArray,
                                override val listeningHostPort: Pair<String, Int>
) : PeerCommConfiguration {

    companion object {
        // Used in tests only
        fun build(peers: Array<PeerInfo>,
                  cryptoSystem: CryptoSystem,
                  privKey: ByteArray,
                  pubKey: ByteArray
        ): BasePeerCommConfiguration {
            return build(peers.toSet(), cryptoSystem, privKey, pubKey, Pair("", 0))
        }

        fun build(peers: Collection<PeerInfo>,
                  cryptoSystem: CryptoSystem,
                  privKey: ByteArray,
                  pubKey: ByteArray,
                  listeningHostPort: Pair<String, Int>
        ): BasePeerCommConfiguration {
            val nn = NetworkNodes.buildNetworkNodes(peers, ByteArrayKey(pubKey))
            //If listening host or port not set in node.properties, use access host port for server binding.
            val host = if (listeningHostPort.first == "") nn.myself.host else listeningHostPort.first
            val port = if (listeningHostPort.second == 0) nn.myself.port else listeningHostPort.second
            return BasePeerCommConfiguration(nn, cryptoSystem, privKey, pubKey, Pair(host, port))
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