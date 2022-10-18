package net.postchain.network.mastersub.subnode

import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.network.peer.DefaultPeersCommConfigFactory

class DefaultSubPeersCommConfigFactory : DefaultPeersCommConfigFactory() {

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {
        val config = super.create(appConfig, nodeConfig, blockchainConfig, historicBlockchainContext)

        return DefaultSubPeerCommConfig(config, getChainPeersFromConfig(historicBlockchainContext, blockchainConfig))
    }

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {
        val config = super.create(appConfig, nodeConfig, blockchainRid, peers, historicBlockchainContext)

        return DefaultSubPeerCommConfig(config, peers)
    }
}