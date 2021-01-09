package net.postchain.containers.bpm

import net.postchain.base.BlockchainRid
import net.postchain.containers.NameService
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.masterslave.master.MasterCommunicationManager

enum class ProcessState {
    UNDEFINED, STARTING, RUNNING, STOPPING
}

interface ContainerBlockchainProcess {
    val processName: BlockchainProcessName
    val chainId: Long
    val blockchainRid: BlockchainRid
    val containerName: String
    var state: ProcessState
    var containerId: String?
}

class DefaultContainerBlockchainProcess(
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        nodePubKey: String,
        private val communicationManager: MasterCommunicationManager,
        override val containerName: String = NameService.containerName(nodePubKey, chainId, blockchainRid),
        override var state: ProcessState = ProcessState.UNDEFINED,
        override var containerId: String? = null
) : ContainerBlockchainProcess
