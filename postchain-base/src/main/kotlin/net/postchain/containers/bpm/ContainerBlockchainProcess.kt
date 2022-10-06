package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.common.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.RemoteBlockchainProcess
import net.postchain.core.Shutdownable
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.network.mastersub.master.MasterCommunicationManager
import java.net.URL

interface ContainerBlockchainProcess : RemoteBlockchainProcess, HeartbeatListener, Shutdownable {
    val processName: BlockchainProcessName

    override fun checkHeartbeat(timestamp: Long) = true
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        val containerNodeConfig: ContainerNodeConfig,
        restApiPort: Int,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        private val communicationManager: MasterCommunicationManager,
) : ContainerBlockchainProcess {

    companion object : KLogging()

    override val restApiUrl = URL("http",
            containerNodeConfig.subnodeHost,
            restApiPort,
            RestApiConfig.fromAppConfig(nodeConfig.appConfig).basePath
    ).toString()

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        communicationManager.sendHeartbeatToSub(heartbeatEvent)
        logger.debug { "Heartbeat event sent to ${processName}: timestamp ${heartbeatEvent.timestamp}" }
    }

    override fun shutdown() {
        communicationManager.shutdown()
    }

    override fun toString(): String = processName.toString()
}
