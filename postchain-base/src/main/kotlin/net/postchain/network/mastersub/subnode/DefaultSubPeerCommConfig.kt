package net.postchain.network.mastersub.subnode

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo
import net.postchain.core.NodeRid
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Key

class DefaultSubPeerCommConfig(
        networkNodes: NetworkNodes,
        cryptoSystem: CryptoSystem,
        privKey: Key,
        pubKey: Key,
        override val peers: List<ByteArray>
) : BasePeerCommConfiguration(
        networkNodes,
        cryptoSystem,
        privKey,
        pubKey
), SubPeerCommConfig {

    companion object {
        fun build(
                peerInfoMap: Map<NodeRid, PeerInfo>,
                cryptoSystem: CryptoSystem,
                privKey: Key,
                pubKey: Key,
                peers: List<ByteArray>
        ): DefaultSubPeerCommConfig {
            val nn = NetworkNodes.buildNetworkNodes(peerInfoMap.values, net.postchain.core.ByteArrayKey(pubKey.byteArray))
            return DefaultSubPeerCommConfig(nn, cryptoSystem, privKey, pubKey, peers)
        }
    }
}
