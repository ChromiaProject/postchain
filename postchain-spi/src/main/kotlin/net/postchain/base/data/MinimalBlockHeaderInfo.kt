package net.postchain.base.data

import net.postchain.core.BlockRid


/**
 * Holds minimal data needed for validation
 */
data class MinimalBlockHeaderInfo(
        val headerBlockRid: BlockRid,
        val headerPrevBlockRid: BlockRid?, // Doesn't have to have a prev block
        val headerHeight: Long)