package net.postchain.managed

import net.postchain.base.HistoricBlockchainContext
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.core.NodeRid
import net.postchain.network.peer.DefaultPeersCommConfigFactory

class DefaultManagedPeersCommConfigFactory : DefaultPeersCommConfigFactory() {
    override fun getExtraBlockchainReplicaNodes(appConfig: AppConfig,
                                                nodeConfig: NodeConfig,
                                                chainId: Long,
                                                blockchainRid: BlockchainRid,
                                                signers: List<ByteArray>,
                                                historicBlockchainContext: HistoricBlockchainContext?): Set<NodeRid> {
        return if (chainId == CHAIN0)
                appConfig.initialPeer?.pubKey?.let { setOf(NodeRid(it)) } ?: emptySet()
            else
                emptySet()
    }
}
