package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerImageInfo
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
    fun getResourceLimitForContainer(container: String): ContainerResourceLimits

    /**
     * Returns the Docker image required to run subnode for container,
     * or `null` if the container does not specify any image.
     */
    fun getImageForContainer(container: String): ContainerImageInfo?

    /**
     * Returns the Docker image required to run subnode for container,
     * or the default subnode image if the container does not specify any image,
     * or `null` if default image is not specified in directory chain.
     */
    fun getImageForContainerOrDefault(container: String): ContainerImageInfo?
}