package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.master.MasterCommunicationManager
import java.nio.file.Path
import java.util.*

interface ContainerBlockchainProcess {
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
        private val chainConfigsDir: Path
) : ContainerBlockchainProcess {

    companion object : KLogging()

    private lateinit var configTimer: Timer // TODO: [POS-129]: Implement shared config timer
    private var lastHeight = -1L

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
                val configPath = chainConfigsDir.resolve("$height.gtv")
                configPath.toFile().writeBytes(config) // TODO: [POS-129]: Add exceptions handling
                logger.info("Config file dumped: $configPath")
                lastHeight = height
            }
        }
    }
}
