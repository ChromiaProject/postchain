package net.postchain.containers.bpm.job

import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.exception.UserMistake
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.logging.CONTAINER_NAME_TAG
import org.mandas.docker.client.DockerClient

class ContainerHealthcheckHandler(
        private val dockerClient: DockerClient,
        private val fileSystem: FileSystem,
        private val postchainContainers: () -> MutableMap<ContainerName, PostchainContainer>,
        private val removeBlockchainProcess: (Long, PostchainContainer) -> ContainerBlockchainProcess?
) {

    companion object : KLogging()

    fun check(containersInProgress: Set<String>) {
        checkInternal(containersInProgress)
    }

    private fun checkInternal(containersInProgress: Set<String>) {
        val start = System.currentTimeMillis()
        logger.debug { "ContainerHealthcheck: BEGIN" }

        val containersToCheck = postchainContainers().keys
                .associateBy { it.dockerContainer }
                .filter { it.key !in containersInProgress }

        logger.info {
            "ContainerHealthcheck: containersInProgress: $containersInProgress, containersToCheck: ${containersToCheck.keys}"
        }

        val fixedContainers = mutableSetOf<ContainerName>()
        if (containersToCheck.isNotEmpty()) {
            val running = dockerClient.listContainers() // running containers only
            containersToCheck.values.forEach { cname ->
                withLoggingContext(CONTAINER_NAME_TAG to cname.dockerContainer) {
                    val containerIsRunning = running.any { it.hasName(cname.dockerContainer) }
                    checkContainer(cname, containerIsRunning, fixedContainers)
                }
            }
        }

        if (fixedContainers.isEmpty()) {
            logger.info { "ContainerHealthcheck: Ok" }
        } else {
            logger.info { "ContainerHealthcheck: Fixed: $fixedContainers" }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "ContainerHealthcheck: END ($elapsed ms)" }
    }

    private fun checkContainer(cname: ContainerName, containerIsRunning: Boolean, fixedContainers: MutableSet<ContainerName>) {
        val psContainer = postchainContainers()[cname]!!
        val currentResourceLimits = psContainer.resourceLimits
        val currentImage = psContainer.image
        val updatedResourceLimits = try {
            psContainer.updateResourceLimits()
        } catch (e: UserMistake) {
            logger.warn { "Unable to fetch current container limits for container '${psContainer.containerName.directoryContainer}' from directory chain: ${e.message}" }
            false
        }
        val updatedImage = try {
            psContainer.updateImage()
        } catch (e: UserMistake) {
            logger.warn { "Unable to fetch current image for container '${psContainer.containerName.directoryContainer}' from directory chain: ${e.message}" }
            false
        }
        val chainsToTerminate = if (updatedResourceLimits || updatedImage) {
            if (updatedResourceLimits) {
                logger.info { "Resource limits for container ${cname.dockerContainer} have been changed from $currentResourceLimits to ${psContainer.resourceLimits}, container will be restarted" }
            }
            if (updatedImage) {
                logger.info { "Image for container ${cname.dockerContainer} has been changed from $currentImage to ${psContainer.image}, container will be restarted" }
            }
            fixedContainers.add(cname)
            psContainer.reset()
            if (containerIsRunning) dockerClient.stopContainer(cname.dockerContainer, 10)
            dockerClient.removeContainer(cname.dockerContainer)
            psContainer.getAllChains().toSet()
        } else if (!containerIsRunning) {
            logger.warn { "Subnode container is not running and will be restarted: ${cname.dockerContainer}" }
            fixedContainers.add(cname)
            psContainer.getAllChains().toSet()
        } else if (!psContainer.isSubnodeHealthy()) {
            logger.warn { "Subnode container is unhealthy and will be restarted: ${cname.dockerContainer}" }
            fixedContainers.add(cname)
            dockerClient.stopContainer(cname.dockerContainer, 10)
            psContainer.getAllChains().toSet()
        } else if (!psContainer.checkResourceLimits(fileSystem)) {
            logger.warn { "Subnode container has reached resource limits and will be restarted: ${cname.dockerContainer}" }
            fixedContainers.add(cname)
            psContainer.reset()
            if (containerIsRunning) dockerClient.stopContainer(cname.dockerContainer, 10)
            dockerClient.removeContainer(cname.dockerContainer)
            psContainer.getAllChains().toSet()
        } else {
            logger.debug { "Subnode container is running and healthy: ${cname.dockerContainer}" }
            psContainer.getStoppedChains().toSet()
        }

        chainsToTerminate.forEach {
            removeBlockchainProcess(it, psContainer)
        }
        if (chainsToTerminate.isNotEmpty()) {
            logger.info { "Container chains have been terminated: $chainsToTerminate" }
        }
    }
}
