package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
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
        val rid: WrappedByteArray,
        val sourceContainer: String,
        val destinationContainer: String,
        val isSourceNode: Boolean,
        val isDestinationNode: Boolean,
        val upToHeight: Long
)
