package net.postchain.managed

import net.postchain.core.BlockchainState

data class LocalBlockchainInfo(
        val chainId: Long,
        val system: Boolean,
        val state: BlockchainState
)
