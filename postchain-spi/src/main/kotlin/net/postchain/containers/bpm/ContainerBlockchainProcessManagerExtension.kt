package net.postchain.containers.bpm

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainProcessManagerExtension

interface ContainerBlockchainProcessManagerExtension : BlockchainProcessManagerExtension {
    fun afterCommitInSubnode(blockchainRid: BlockchainRid, blockHeight: Long)
}
