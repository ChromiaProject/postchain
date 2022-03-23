package net.postchain.network.peer

import net.postchain.core.BlockchainRid
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration

interface PeersCommConfigFactory {

    fun create(
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration

    fun create(
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration

}