package net.postchain.network.mastersub.master

import net.postchain.common.BlockchainRid
import net.postchain.network.mastersub.MsMessageHandler

data class SubChainConfig(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val messageHandler: MsMessageHandler
)
