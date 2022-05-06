package net.postchain.devtools.utils

import net.postchain.common.BlockchainRid

/**
 * (broken out to here from Kalle's code, thought it could be re-used)
 */
object ChainUtil {

    /**
     * Simple BC Iid -> BC RIDs (for test only)
     */
    fun ridOf(chainIid: Long): BlockchainRid {
        val hexChainIid = chainIid.toString(8)
        val base = "0000000000000000000000000000000000000000000000000000000000000000"
        val rid = base.substring(0, 64-hexChainIid.length) + hexChainIid
        return BlockchainRid.buildFromHex(rid)
    }

    /**
     * Simple BC RID -> BC Iid converter (for test only)
     */
    fun iidOf(brid: BlockchainRid): Long {
        return brid.toHex().toLong(8)
    }
}