package net.postchain.containers.bpm

import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
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
    val restApiPort: Int
    var containerId: String?
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        private val communicationManager: MasterCommunicationManager,
        override var state: ProcessState = ProcessState.UNDEFINED,
        override var containerId: String? = null
) : ContainerBlockchainProcess {

    override val containerName: String = NameService.containerName(nodeConfig.pubKey, chainId, blockchainRid)
    override val restApiPort: Int = nodeConfig.restApiPort + 10 * chainId.toInt() // TODO: [POS-129]: Change this

}
