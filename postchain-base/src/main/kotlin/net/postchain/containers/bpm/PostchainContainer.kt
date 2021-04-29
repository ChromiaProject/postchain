package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.config.node.NodeConfig
import net.postchain.containers.NameService
import net.postchain.managed.DirectoryDataSource
import java.util.*
import kotlin.concurrent.timer

enum class ContainerState {
    UNDEFINED, STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    val containerName: String
    var state: ContainerState
    var containerId: String
    var blockchainProcesses: MutableSet<ContainerBlockchainProcess>

    fun start()
    fun stop()
    val nodeContainerName: String
}

class DefaultPostchainContainer(
        val nodeConfig: NodeConfig,
        override var blockchainProcesses: MutableSet<ContainerBlockchainProcess>,
        private val dataSource: DirectoryDataSource,
        override var state: ContainerState = ContainerState.UNDEFINED,
        override val containerName: String
) : PostchainContainer {

    companion object : KLogging()

    override val nodeContainerName: String = NameService.extendedContainerName(nodeConfig.pubKey, containerName)
//override val restApiPort: Int = nodeConfig.restApiPort + 10 * chainId.toInt() // TODO: [POS-129]: Change this
//    override val restApiPort: Int = nodeConfig.restApiPort + containerName.toInt() // TODO: [POS-129]: Change this
    private lateinit var configTimer: Timer // TODO: [POS-129]: Implement shared config timer
    private var lastHeight = -1L
    override lateinit var containerId: String

    override fun start() {
        state = ContainerState.RUNNING
        setResourceLimitsForContainer()
        // TODO: [POS-129]: Calc period basing on blockchain-config.maxblocktime param
        configTimer = timer(name = "timer-$containerName", period = 1000L) {
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
    private fun setResourceLimitsForContainer() {
        val limits = dataSource.getResourceLimitForContainer(containerName)
        if (limits != null) {
            limits.forEach { s, l ->  } //TODO:transfer limits to docker.
        }
    }
}
