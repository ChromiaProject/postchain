package net.postchain.network.peer

import net.postchain.common.BlockchainRid
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration

interface PeersCommConfigFactory {

    fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration

    fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            chainId: Long,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration

    fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            ancestorBlockchainRid: BlockchainRid,
            historicBlockchainContext: HistoricBlockchainContext
    ): PeerCommConfiguration

}