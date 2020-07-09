package net.postchain.network.masterslave.slave

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.CryptoSystem
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo
import net.postchain.core.ByteArrayKey
import net.postchain.network.x.XPeerID

class DefaultSlavePeerCommConfiguration(
        networkNodes: NetworkNodes,
        cryptoSystem: CryptoSystem,
        privKey: ByteArray,
        pubKey: ByteArray,
        override val singers: List<ByteArray>
) : BasePeerCommConfiguration(
        networkNodes,
        cryptoSystem,
        privKey,
        pubKey
), SlavePeerCommConfiguration {

    companion object {
        fun build(
                peerInfoMap: Map<XPeerID, PeerInfo>,
                cryptoSystem: CryptoSystem,
                privKey: ByteArray,
                pubKey: ByteArray,
                singers: List<ByteArray>
        ): DefaultSlavePeerCommConfiguration {
            val nn = NetworkNodes.buildNetworkNodes(peerInfoMap.values, ByteArrayKey(pubKey))
            return DefaultSlavePeerCommConfiguration(nn, cryptoSystem, privKey, pubKey, singers)
        }
    }

}