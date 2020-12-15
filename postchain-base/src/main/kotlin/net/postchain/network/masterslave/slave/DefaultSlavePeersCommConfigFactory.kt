package net.postchain.network.masterslave.slave

import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.node.NodeConfig
import net.postchain.network.x.DefaultPeersCommConfigFactory

class DefaultSlavePeersCommConfigFactory : DefaultPeersCommConfigFactory() {

    override fun create(
            nodeConfig: NodeConfig,
            chainId: Long,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>
    ): PeerCommConfiguration {

        val relevantPeerMap = buildPeersMap(nodeConfig, blockchainRid, peers)

        return DefaultSlavePeerCommConfig.build(
                relevantPeerMap,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray,
                peers)
    }
}