package net.postchain.network.mastersub.master

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockRid

fun interface AfterSubnodeCommitListener {
    fun onAfterCommit(blockchainRid: BlockchainRid, blockRid: BlockRid, blockHeader: ByteArray, witnessData: ByteArray)
}
