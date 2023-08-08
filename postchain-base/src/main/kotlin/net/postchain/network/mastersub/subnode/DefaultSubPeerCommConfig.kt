package net.postchain.network.mastersub.subnode

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo
import net.postchain.config.app.AppConfig
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
                appConfig: AppConfig,
                peers: List<ByteArray>
        ): DefaultSubPeerCommConfig {
            val nn = NetworkNodes.buildNetworkNodes(peerInfoMap.values, appConfig)
            return DefaultSubPeerCommConfig(
                    nn,
                    appConfig.cryptoSystem,
                    appConfig.privKeyByteArray,
                    appConfig.pubKeyByteArray,
                    peers)
        }
    }
}
