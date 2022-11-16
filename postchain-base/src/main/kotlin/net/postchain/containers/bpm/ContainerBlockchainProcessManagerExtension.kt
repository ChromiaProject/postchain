package net.postchain.containers.bpm

import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.core.RemoteBlockchainProcess

interface ContainerBlockchainProcessManagerExtension : BlockchainProcessManagerExtension {
    fun afterCommitInSubnode(process: RemoteBlockchainProcess, blockRid: ByteArray, blockHeader: ByteArray, witnessData: ByteArray)
}
