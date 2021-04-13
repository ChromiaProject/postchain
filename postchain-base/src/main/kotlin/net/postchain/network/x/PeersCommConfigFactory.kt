package net.postchain.network.x

import net.postchain.base.BlockchainRid
import net.postchain.base.HistoricBlockchain
import net.postchain.base.PeerCommConfiguration
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration

interface PeersCommConfigFactory {

    fun create(
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchain: HistoricBlockchain?
    ): PeerCommConfiguration

    fun create(
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchain: HistoricBlockchain?
    ): PeerCommConfiguration

}