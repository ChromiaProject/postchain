package net.postchain.core.block

import net.postchain.common.BlockchainRid

/**
 * This is a DTO we will use to build a block.
 * Note that we don't hold the RID of the block itself, b/c we don't know it yet.
 *
 * @property blockHeightDependencyArr holds all the Block RIDs of the last block of all this blockchain's dependencies.
 *           ("null" means this blockchain doesn't have any dependencies)
 */
class InitialBlockData(
        val blockchainRid: BlockchainRid,
        val blockIID: Long,
        val chainID: Long,
        val prevBlockRID: ByteArray,
        val height: Long,
        val timestamp: Long,
        val blockHeightDependencyArr: Array<ByteArray?>?
)