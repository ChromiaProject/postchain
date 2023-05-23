package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.containers.bpm.docker.DockerTools
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.crypto.PrivKey
import net.postchain.managed.DirectoryDataSource

class DefaultPostchainContainer(
        val dataSource: DirectoryDataSource,
        override val containerName: ContainerName,
        override var containerPortMapping: MutableMap<Int, Int>,
        override var state: ContainerState,
        private val subnodeAdminClient: SubnodeAdminClient,
        override var containerId: String? = null,
) : PostchainContainer {

    companion object : KLogging()

    private val processes = mutableMapOf<Long, ContainerBlockchainProcess>()
    private var initialized = false

    // NB: Resources are per directoryContainerName, not nodeContainerName
    @Volatile
    override var resourceLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)

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
        subnodeAdminClient.startBlockchain(process.chainId, process.blockchainRid).also {
            if (it) processes[process.chainId] = process
        }
        return true
    }

    override fun removeProcess(chainId: Long): ContainerBlockchainProcess? = processes.remove(chainId)

    override fun terminateProcess(chainId: Long): ContainerBlockchainProcess? {
        return processes.remove(chainId)?.also {
            subnodeAdminClient.stopBlockchain(chainId)
        }
    }

    override fun terminateAllProcesses(): Set<Long> {
        return getAllChains().toSet().onEach(::terminateProcess)
    }

    override fun getBlockchainLastHeight(chainId: Long): Long {
        return subnodeAdminClient.getBlockchainLastHeight(chainId)
    }

    override fun start() {
        state = ContainerState.RUNNING
        subnodeAdminClient.connect()
    }

    override fun reset() {
        subnodeAdminClient.disconnect()
        state = ContainerState.STARTING
        initialized = false
    }

    override fun stop() {
        state = ContainerState.STOPPING
        subnodeAdminClient.shutdown()
    }

    override fun isEmpty() = processes.isEmpty()

    override fun isSubnodeHealthy() = subnodeAdminClient.isSubnodeHealthy()

    override fun initializePostchainNode(privKey: PrivKey): Boolean = if (!initialized) {
        val success = subnodeAdminClient.initializePostchainNode(privKey)
        if (success) initialized = true
        success
    } else true

    override fun updateResourceLimits(): Boolean {
        val oldResourceLimits = resourceLimits
        val newResourceLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)
        return if (newResourceLimits != oldResourceLimits) {
            resourceLimits = newResourceLimits
            true
        } else {
            false
        }
    }
}
