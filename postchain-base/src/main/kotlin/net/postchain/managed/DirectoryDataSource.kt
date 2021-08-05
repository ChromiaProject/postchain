package net.postchain.managed

import net.postchain.base.BlockchainRid
import net.postchain.containers.infra.ContainerResourceType

interface DirectoryDataSource : ManagedNodeDataSource {

    /**
     * I'm a node, unique to this cluster. What containers should I run?
     */
    fun getContainersToRun(): List<String>?

    /**
     * Which blockchains to run in which container?
     */
    fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>?
    fun getContainerForBlockchain(brid: BlockchainRid): String?

    /**
     * What is the resource limits for this container?
     */
    fun getResourceLimitForContainer(containerID: String): Map<ContainerResourceType, Long>?

    fun setLimitsForContainer(containerID: String, ramLimit: Long, cpuQuota: Long)

}