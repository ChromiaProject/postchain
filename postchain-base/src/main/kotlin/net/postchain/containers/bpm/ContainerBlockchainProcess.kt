package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.master.MasterCommunicationManager
import java.io.File
import java.util.*

interface ContainerBlockchainProcess : HeartbeatListener {
    val processName: BlockchainProcessName
    val chainId: Long
    val blockchainRid: BlockchainRid
    val restApiPort: Int

    fun transferConfigsToContainer()
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        override val restApiPort: Int,
        private val communicationManager: MasterCommunicationManager,
        private val dataSource: DirectoryDataSource,
        private val containerChainDir: ContainerChainDir
) : ContainerBlockchainProcess {

    companion object : KLogging()

    private lateinit var configTimer: Timer // TODO: [POS-129]: Implement shared config timer
    private var lastHeight = -1L

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        communicationManager.sendHeartbeatToSlave(heartbeatEvent)
    }

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
                val filename = containerChainDir.resolveChainFilename("$height.gtv")
                File(filename).writeBytes(config) // TODO: [POS-129]: Add exceptions handling
                logger.info("Config file dumped: $filename")
                lastHeight = height
            }
        }
    }
}
