package net.postchain.managed

import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.common.BlockchainRid

interface DirectoryDataSource : ManagedNodeDataSource {

    /**
     * I'm a node, unique to this cluster. What containers should I run?
     */
    fun getContainersToRun(): List<String>?

    /**
     * Which blockchains to run in which container?
     */
    fun getBlockchainsForContainer(containerId: String): List<BlockchainRid>?

    /**
     * TODO: [POS-164]: Provide a KDoc
     */
    fun getContainerForBlockchain(brid: BlockchainRid): String

    /**
     * What is the resource limits for this container?
     */
    fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits

    fun setLimitsForContainer(containerId: String, ramLimit: Long, cpuQuota: Long)

}