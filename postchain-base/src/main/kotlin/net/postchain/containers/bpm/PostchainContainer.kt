package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.containers.bpm.rpc.ContainerPorts
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.managed.DirectoryDataSource

enum class ContainerState {
    STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    val containerName: ContainerName
    var state: ContainerState
    var containerId: String?
    val resourceLimits: ContainerResourceLimits
    val containerPorts: ContainerPorts

    fun shortContainerId(): String?
    fun findProcesses(chainId: Long): ContainerBlockchainProcess?
    fun getAllChains(): Set<Long>
    fun getStoppedChains(): Set<Long>
    fun startProcess(process: ContainerBlockchainProcess): Boolean
    fun terminateProcess(chainId: Long): ContainerBlockchainProcess?
    fun terminateAllProcesses(): Set<Long>
    fun start()
    fun stop()
    fun isEmpty(): Boolean
    fun isSubnodeConnected(): Boolean
}


class DefaultPostchainContainer(
        val dataSource: DirectoryDataSource,
        override val containerName: ContainerName,
        override var containerPorts: ContainerPorts,
        override var state: ContainerState,
        private val subnodeAdminClient: SubnodeAdminClient,
        override var containerId: String? = null,
) : PostchainContainer {

    companion object : KLogging()

    private val processes = mutableMapOf<Long, ContainerBlockchainProcess>()

    // NB: Resources are per directoryContainerName, not nodeContainerName
    override val resourceLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)

    override fun shortContainerId(): String? {
        return DockerTools.shortContainerId(containerId)
    }

    override fun findProcesses(chainId: Long): ContainerBlockchainProcess? {
        return processes[chainId]
    }

    override fun getAllChains(): Set<Long> = processes.keys
    override fun getStoppedChains(): Set<Long> {
        return processes.keys
                .filter { !subnodeAdminClient.isBlockchainRunning(it) }
                .toSet()
    }

    override fun startProcess(process: ContainerBlockchainProcess): Boolean {
        val config0 = dataSource.getConfiguration(process.blockchainRid.data, 0L)
        return if (config0 != null) {
            subnodeAdminClient.startBlockchain(process.chainId, config0).also {
                if (it) processes[process.chainId] = process
            }
        } else {
            logger.error { "Can't start process: config at height 0 is absent" }
            false
        }
    }

    override fun terminateProcess(chainId: Long): ContainerBlockchainProcess? {
        return processes.remove(chainId)?.also {
            subnodeAdminClient.stopBlockchain(chainId)
        }
    }

    override fun terminateAllProcesses(): Set<Long> {
        return getAllChains().toSet().onEach(::terminateProcess)
    }

    override fun start() {
        state = ContainerState.RUNNING
        subnodeAdminClient.connect()
    }

    override fun stop() {
        state = ContainerState.STOPPING
        subnodeAdminClient.shutdown()
    }

    override fun isEmpty() = processes.isEmpty()

    override fun isSubnodeConnected() = subnodeAdminClient.isSubnodeConnected()
}
