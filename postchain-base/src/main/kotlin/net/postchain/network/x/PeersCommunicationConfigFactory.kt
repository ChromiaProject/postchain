package net.postchain.network.x

import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.config.node.NodeConfig

interface PeersCommunicationConfigFactory {

    fun create(
            nodeConfig: NodeConfig,
            chainId: Long,
            blockchainRid: BlockchainRid,
            signers: List<ByteArray>
    ): PeerCommConfiguration

}