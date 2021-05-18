package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.node.NodeConfig
import net.postchain.managed.DirectoryDataSource
import java.util.*
import kotlin.concurrent.timer

enum class ContainerState {
    UNDEFINED, STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    var state: ContainerState
    var containerId: String
    var blockchainProcesses: MutableSet<ContainerBlockchainProcess>
    var resourceLimits: Map<String, Long>?

    fun contains(chainId: Long): Boolean
    fun getChains(): Set<Long>
    fun start()
    fun stop()
    val directoryContainerName: String
    val nodeContainerName: String
}

class DefaultPostchainContainer(
        val nodeConfig: NodeConfig,
        override var blockchainProcesses: MutableSet<ContainerBlockchainProcess>,
        private val dataSource: DirectoryDataSource,
        override var state: ContainerState = ContainerState.UNDEFINED,
        val containerName: Map<String, String?>
) : PostchainContainer {

    companion object : KLogging()

    override var resourceLimits: Map<String, Long>?
        //NB: Resources are found per directoryContainerName, not nodeContainerName
        get() = dataSource.getResourceLimitForContainer(directoryContainerName)
        set(value) {}

    override val directoryContainerName = containerName["directory"]!!
    override val nodeContainerName = containerName["node"]!!

    private lateinit var configTimer: Timer // TODO: [POS-129]: Implement shared config timer
    private var lastHeight = -1L
    override lateinit var containerId: String

    override fun contains(chainId: Long): Boolean {
        return (blockchainProcesses.any { it.chainId == chainId })
    }

    override fun getChains(): Set<Long> = blockchainProcesses.map { it.chainId }.toSet()

    override fun start() {
        state = ContainerState.RUNNING
        // TODO: [POS-129]: Calc period basing on blockchain-config.maxblocktime param
        configTimer = timer(name = "timer-$nodeContainerName", period = 1000L) {
            blockchainProcesses.forEach{ it.transferConfigsToContainer()}
        }
    }

    override fun stop() {
        state = ContainerState.STOPPING
        if (this::configTimer.isInitialized) {
            configTimer.cancel()
            configTimer.purge()
        }
    }
}
