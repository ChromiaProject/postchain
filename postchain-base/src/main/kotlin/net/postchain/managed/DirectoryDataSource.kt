package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.containers.ContainerRateLimit
import net.postchain.containers.bpm.ContainerImageInfo
import net.postchain.containers.bpm.ContainerResourceLimits
import java.time.Instant

interface DirectoryDataSource : ManagedNodeDataSource {

    /**
     * I'm a node, unique to this cluster. What containers should I run?
     */
    fun getContainersToRun(): List<String>?

    /**
     * Get all containers (ignoring state) for this node.
     * @return A list of container names for this node, or null if the query failed.
     */
    fun getNodeContainers(): List<String>?

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
     * What are the resource limits for this container?
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
     * or `null` if default image is not specified in the directory chain.
     */
    fun getImageForContainerOrDefault(container: String): ContainerImageInfo?

    /**
     * Returns the time when the container was created,
     * or `null` if that information is not available.
     */
    fun getContainerCreationTime(container: String): Instant?

    /**
     * Returns rate limit configuration for a container as a map of task types to rate limit configuration for that type of tasks.
     * If there is no rate limit for a particular task type for a container, it will be absent in the map.
     * If there are no rate limits for anything for a container, the returned map will be empty.
     */
    fun getContainerRateLimits(container: String): Map<String, ContainerRateLimit>
}
