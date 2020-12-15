package net.postchain.network.masterslave.master

import net.postchain.base.BlockchainRid
import net.postchain.network.masterslave.MsMessageHandler

data class SlaveChainConfig(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val messageHandler: MsMessageHandler
)
