package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainState

data class BlockchainInfo(
        val rid: BlockchainRid,
        val system: Boolean,
        val state: BlockchainState
)
