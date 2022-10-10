package net.postchain.core.block

import net.postchain.common.BlockchainRid

fun interface BlockQueriesProvider {
    fun getBlockQueries(blockchainRid: BlockchainRid): BlockQueries?
}