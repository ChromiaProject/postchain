package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.core.Shutdownable
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.network.mastersub.master.MasterCommunicationManager

interface ContainerBlockchainProcess : HeartbeatListener, Shutdownable {
    val processName: BlockchainProcessName
    val chainId: Long
    val blockchainRid: BlockchainRid
    val restApiPort: Int

    override fun checkHeartbeat(timestamp: Long) = true
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        override val restApiPort: Int,
        private val communicationManager: MasterCommunicationManager,
) : ContainerBlockchainProcess {

    companion object : KLogging()

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        communicationManager.sendHeartbeatToSub(heartbeatEvent)
        logger.debug { "Heartbeat event sent to ${processName}: timestamp ${heartbeatEvent.timestamp}" }
    }

    override fun shutdown() {
        communicationManager.shutdown()
    }

    override fun toString(): String = processName.toString()
}
