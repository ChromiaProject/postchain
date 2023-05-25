package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.common.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainState
import net.postchain.core.RemoteBlockchainProcess
import net.postchain.core.Shutdownable
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.mastersub.master.MasterCommunicationManager
import java.net.URL

interface ContainerBlockchainProcess : RemoteBlockchainProcess, Shutdownable {
    val processName: BlockchainProcessName
    val blockchainState: BlockchainState
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        val containerNodeConfig: ContainerNodeConfig,
        restApiPort: Int,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        override val blockchainState: BlockchainState,
        private val communicationManager: MasterCommunicationManager,
) : ContainerBlockchainProcess {

    companion object : KLogging()

    override val restApiUrl = URL("http",
            containerNodeConfig.subnodeHost,
            restApiPort,
            RestApiConfig.fromAppConfig(nodeConfig.appConfig).basePath
    ).toString()

    override fun shutdown() {
        communicationManager.shutdown()
    }

    override fun toString(): String = processName.toString()
}
