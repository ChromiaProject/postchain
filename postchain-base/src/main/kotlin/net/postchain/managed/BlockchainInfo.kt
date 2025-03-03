package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.BlockchainState

data class BlockchainInfo(
        val blockchainRid: BlockchainRid,
        val system: Boolean,
        val state: BlockchainState
)

data class InactiveBlockchainInfo(
        val blockchainRid: BlockchainRid,
        val state: BlockchainState,
        val height: Long
)

data class MigratingBlockchainNodeInfo(
        val migrationRid: WrappedByteArray,
        val sourceContainer: String,
        val destinationContainer: String,
        val isSourceNode: Boolean,
        val isDestinationNode: Boolean,
        val finalHeight: Long
)
