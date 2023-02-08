package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.containers.bpm.docker.DockerTools
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.managed.DirectoryDataSource

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
        val peerInfos = dataSource.getPeerInfos()
        return if (config0 != null) {
            peerInfos.forEach { subnodeAdminClient.addPeerInfo(it) }
            val height = dataSource.getLastBuiltHeight(process.blockchainRid.data)
            logger.info { "Chain ${process.chainId} last built height: $height" }
            if (height != -1L) {
                val currentHeight = height + 1
                val currentConfig = dataSource.getConfiguration(process.blockchainRid.data, currentHeight)
                if (currentConfig != null) {
                    logger.info { "Chain ${process.chainId} config at height $currentHeight will be added to subnode" }
                    subnodeAdminClient.addConfiguration(process.chainId, currentHeight, true, currentConfig)
                } else {
                    logger.info { "There is no a config at height $currentHeight for chain ${process.chainId}" }
                }
            }
            subnodeAdminClient.startBlockchain(process.chainId, process.blockchainRid, config0).also {
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

    override fun getBlockchainLastHeight(chainId: Long): Long {
        return subnodeAdminClient.getBlockchainLastHeight(chainId)
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

    override fun checkForResourceLimitsUpdates(): Pair<Boolean, ContainerResourceLimits?> {
        val newLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)
                .takeIf { resourceLimits != it }
        return (newLimits != null) to newLimits
    }
}