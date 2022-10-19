package net.postchain.network.mastersub.subnode

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.NodeRid
import net.postchain.crypto.CryptoSystem

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
            val nn = NetworkNodes.buildNetworkNodes(peerInfoMap.values, WrappedByteArray(pubKey))
            return DefaultSubPeerCommConfig(nn, cryptoSystem, privKey, pubKey, peers)
        }
    }
}
