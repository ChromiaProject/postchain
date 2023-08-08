package net.postchain.network.mastersub.subnode

import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
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

        val relevantPeerMap = buildRelevantNodeInfoMap(appConfig, nodeConfig, blockchainConfig.blockchainRid, blockchainConfig.signers,
                historicBlockchainContext)

        return DefaultSubPeerCommConfig.build(
                relevantPeerMap,
                appConfig,
                blockchainConfig.signers
        )
    }
}