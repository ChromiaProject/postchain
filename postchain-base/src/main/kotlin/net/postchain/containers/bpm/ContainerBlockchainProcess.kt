package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainRid
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.master.MasterCommunicationManager
import java.nio.file.Path

interface ContainerBlockchainProcess : HeartbeatListener {
    val processName: BlockchainProcessName
    val chainId: Long
    val blockchainRid: BlockchainRid
    val restApiPort: Int

    fun transferConfigsToContainer()
    fun shutdown()
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        override val restApiPort: Int,
        private val communicationManager: MasterCommunicationManager,
        private val dataSource: DirectoryDataSource, // TODO [POS-164]: (!)
        private val containerChainDir: Path,
        private val onShutdown: () -> Unit
) : ContainerBlockchainProcess {

    companion object : KLogging()

    private var lastHeight = -1L

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        logger.debug { "Heartbeat event sent to ${processName}: timestamp ${heartbeatEvent.timestamp}" }
        communicationManager.sendHeartbeatToSlave(heartbeatEvent)
    }

    // TODO: [et]: Transfer only (init) config0
    @Synchronized
    override fun transferConfigsToContainer() {
        // Retrieving configs from dataSource/chain0
        val configs: Map<Long, ByteArray> = try {
            dataSource.getConfigurations(blockchainRid.data).toSortedMap()
        } catch (e: Exception) {
            logger.error("Exception in dataSource.getConfigurations(): " + e.message)
            mapOf()
        }
        // Dumping all chain configs to chain configs dir
        // TODO: [POS-129]: Skip already dumped configs
        val configsToDump = configs.filterKeys { it > lastHeight }
        if (configsToDump.isNotEmpty()) {
            logger.info("Number of chain configs to dump: ${configsToDump.size}")
            configs.filterKeys { it > lastHeight }.forEach { (height, config) ->
                val filepath = containerChainDir.resolve("$height.gtv")
                filepath.toFile().writeBytes(config) // TODO: [POS-129]: Add exceptions handling
                logger.info("Config file dumped: $filepath")
                lastHeight = height
            }
        }
    }

    override fun shutdown() {
        onShutdown()
    }

    override fun toString(): String = processName.toString()
}
