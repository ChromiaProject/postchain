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
    val nodeContainerName: String
}

class DefaultPostchainContainer(
        val nodeConfig: NodeConfig,
        override var blockchainProcesses: MutableSet<ContainerBlockchainProcess>,
        private val dataSource: DirectoryDataSource,
        override var state: ContainerState = ContainerState.UNDEFINED,
        override val nodeContainerName: String
) : PostchainContainer {

    companion object : KLogging()

    override var resourceLimits: Map<String, Long>?
        get() = dataSource.getResourceLimitForContainer(nodeContainerName)
        set(value) {}
//    override val nodeContainerName: String = NameService.extendedContainerName(nodeConfig.pubKey, directoryContainerName)
//override val restApiPort: Int = nodeConfig.restApiPort + 10 * chainId.toInt() // TODO: [POS-129]: Change this
//    override val restApiPort: Int = nodeConfig.restApiPort + containerName.toInt() // TODO: [POS-129]: Change this
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
