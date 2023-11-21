package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainState

data class BlockchainInfo(
        val rid: BlockchainRid,
        val system: Boolean,
        val state: BlockchainState
)

data class InactiveBlockchainInfo(
        val rid: BlockchainRid,
        val state: BlockchainState,
        val height: Long
)

data class UnarchivingBlockchainNodeInfo(
        val rid: BlockchainRid,
        val sourceContainer: String,
        val destinationContainer: String,
        val upToHeight: Long,
        val isSourceNode: Boolean
)
