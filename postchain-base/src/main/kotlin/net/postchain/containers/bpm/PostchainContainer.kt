package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.managed.DirectoryDataSource

enum class ContainerState {
    UNDEFINED, STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    val containerName: ContainerName
    var state: ContainerState
    var containerId: String?
    val resourceLimits: ContainerResourceLimits
    var restApiPort: Int

    fun shortContainerId(): String?
    fun findProcesses(chainId: Long): ContainerBlockchainProcess?
    fun getChains(): Set<Long>
    fun addProcess(process: ContainerBlockchainProcess)
    fun removeProcess(process: ContainerBlockchainProcess)
    fun start()
    fun stop()
    fun isEmpty(): Boolean
}

class DefaultPostchainContainer(
        dataSource: DirectoryDataSource,
        override val containerName: ContainerName,
        override var restApiPort: Int,
        override var state: ContainerState = ContainerState.UNDEFINED,
        override var containerId: String? = null
) : PostchainContainer {

    companion object : KLogging()

    private val processes = mutableSetOf<ContainerBlockchainProcess>()

    // NB: Resources are per directoryContainerName, not nodeContainerName
    override val resourceLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)

    override fun shortContainerId(): String? {
        return DockerTools.shortContainerId(containerId)
    }

    override fun findProcesses(chainId: Long): ContainerBlockchainProcess? {
        return processes.find { it.chainId == chainId }
    }

    override fun getChains(): Set<Long> = processes.map { it.chainId }.toSet()

    override fun addProcess(process: ContainerBlockchainProcess) {
        processes.add(process)
    }

    override fun removeProcess(process: ContainerBlockchainProcess) {
        processes.remove(process)
    }

    override fun start() {
        state = ContainerState.RUNNING
    }

    override fun stop() {
        state = ContainerState.STOPPING
    }

    override fun isEmpty() = processes.isEmpty()
}
