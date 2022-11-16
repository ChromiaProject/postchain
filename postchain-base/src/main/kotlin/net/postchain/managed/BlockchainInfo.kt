package net.postchain.managed

import net.postchain.common.BlockchainRid

data class BlockchainInfo(
        val rid: BlockchainRid,
        val system: Boolean
)
