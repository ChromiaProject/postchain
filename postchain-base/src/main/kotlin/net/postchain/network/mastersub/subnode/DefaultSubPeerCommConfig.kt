package net.postchain.network.mastersub.subnode

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.CryptoSystem
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo
import net.postchain.core.ByteArrayKey
import net.postchain.core.NodeRid

class DefaultSubPeerCommConfig(
        networkNodes: NetworkNodes,
        cryptoSystem: CryptoSystem,
        privKey: ByteArray,
        pubKey: ByteArray,
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
                privKey: ByteArray,
                pubKey: ByteArray,
                peers: List<ByteArray>
        ): DefaultSubPeerCommConfig {
            val nn = NetworkNodes.buildNetworkNodes(peerInfoMap.values, ByteArrayKey(pubKey))
            return DefaultSubPeerCommConfig(nn, cryptoSystem, privKey, pubKey, peers)
        }
    }
}
