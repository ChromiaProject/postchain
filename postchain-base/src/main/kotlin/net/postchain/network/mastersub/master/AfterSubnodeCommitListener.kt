package net.postchain.network.mastersub.master

import net.postchain.common.BlockchainRid

fun interface AfterSubnodeCommitListener {
    fun onAfterCommitInSubnode(blockchainRid: BlockchainRid, blockHeight: Long)
}
