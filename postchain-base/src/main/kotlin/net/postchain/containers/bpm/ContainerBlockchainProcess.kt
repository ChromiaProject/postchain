package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.containers.NameService
import net.postchain.debug.BlockchainProcessName
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.master.MasterCommunicationManager
import java.nio.file.Path
import java.util.*
import kotlin.concurrent.timer

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

    fun transferConfigsToContainer()
    fun start()
    fun stop()
}

class DefaultContainerBlockchainProcess(
        val nodeConfig: NodeConfig,
        override val processName: BlockchainProcessName,
        override val chainId: Long,
        override val blockchainRid: BlockchainRid,
        private val communicationManager: MasterCommunicationManager,
        private val dataSource: DirectoryDataSource,
        private val chainConfigsDir: Path,
        override var state: ProcessState = ProcessState.UNDEFINED,
        override var containerId: String? = null
) : ContainerBlockchainProcess {

    companion object : KLogging()

    override val containerName: String = NameService.containerName(nodeConfig.pubKey, chainId, blockchainRid)
    override val restApiPort: Int = nodeConfig.restApiPort + 10 * chainId.toInt() // TODO: [POS-129]: Change this
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

    override fun start() {
        state = ProcessState.RUNNING
        // TODO: [POS-129]: Calc period basing on blockchain-config.maxblocktime param
        configTimer = timer(name = "timer-$processName", period = 1000L) {
            transferConfigsToContainer()
        }
    }

    override fun stop() {
        state = ProcessState.STOPPING
        if (this::configTimer.isInitialized) {
            configTimer.cancel()
            configTimer.purge()
        }
    }
}
