package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.node.NodeConfig
import net.postchain.containers.infra.ContainerResourceType
import net.postchain.managed.DirectoryDataSource
import java.util.*
import kotlin.concurrent.timer

enum class ContainerState {
    UNDEFINED, STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    val containerName: ContainerName
    var state: ContainerState
    var containerId: String
    val processes: MutableSet<ContainerBlockchainProcess>
    var resourceLimits: Map<ContainerResourceType, Long>?

    fun contains(chainId: Long): Boolean
    fun getChains(): Set<Long>
    fun start()
    fun stop()
}

class DefaultPostchainContainer(
        val nodeConfig: NodeConfig,
        private val dataSource: DirectoryDataSource,
        override val containerName: ContainerName,
        override val processes: MutableSet<ContainerBlockchainProcess>,
        override var state: ContainerState = ContainerState.UNDEFINED
) : PostchainContainer {

    companion object : KLogging()

    //NB: Resources are per directoryContainerName, not nodeContainerName
    override var resourceLimits = dataSource.getResourceLimitForContainer(containerName.directory)

    private lateinit var configTimer: Timer // TODO: [POS-129]: Implement shared config timer
    private var lastHeight = -1L
    override lateinit var containerId: String

    override fun contains(chainId: Long): Boolean {
        return (processes.any { it.chainId == chainId })
    }

    override fun getChains(): Set<Long> = processes.map { it.chainId }.toSet()

    override fun start() {
        state = ContainerState.RUNNING
        // TODO: [POS-129]: Calc period basing on blockchain-config.maxblocktime param
        configTimer = timer(name = "timer-$containerName", period = 1000L) {
            processes.forEach { it.transferConfigsToContainer() }
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
