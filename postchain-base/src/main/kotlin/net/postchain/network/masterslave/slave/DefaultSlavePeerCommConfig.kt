package net.postchain.network.masterslave.slave

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.CryptoSystem
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo
import net.postchain.core.ByteArrayKey
import net.postchain.network.x.XPeerID

class DefaultSlavePeerCommConfig(
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
), SlavePeerCommConfig {

    companion object {
        fun build(
                peerInfoMap: Map<XPeerID, PeerInfo>,
                cryptoSystem: CryptoSystem,
                privKey: ByteArray,
                pubKey: ByteArray,
                peers: List<ByteArray>
        ): DefaultSlavePeerCommConfig {
            val nn = NetworkNodes.buildNetworkNodes(peerInfoMap.values, ByteArrayKey(pubKey))
            return DefaultSlavePeerCommConfig(nn, cryptoSystem, privKey, pubKey, peers)
        }
    }
}
