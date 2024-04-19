package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerResourceLimits

interface DirectoryDataSource : ManagedNodeDataSource {

    /**
     * I'm a node, unique to this cluster. What containers should I run?
     */
    fun getContainersToRun(): List<String>?

    /**
     * Returns container blockchain is running in.
     * NM API Version: 3
     */
    fun getContainerForBlockchain(brid: BlockchainRid): String

    /**
     * Returns a list of containers running on a node.
     * NM API Version: 14
     */
    fun getBlockchainContainersForNode(brid: BlockchainRid): List<String>

    /**
     * What is the resource limits for this container?
     */
    fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits
}