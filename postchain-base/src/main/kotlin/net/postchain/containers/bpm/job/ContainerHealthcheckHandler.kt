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
        logger.debug { "BEGIN" }

        val containersToCheck = postchainContainers().keys
                .associateBy { it.name }
                .filter { it.key !in containersInProgress }

        logger.info {
            "containersInProgress: $containersInProgress, containersToCheck: ${containersToCheck.keys}"
        }

        val fixedContainers = mutableSetOf<ContainerName>()
        if (containersToCheck.isNotEmpty()) {
            val running = dockerClient.listContainers() // running containers only
            containersToCheck.values.forEach { cname ->
                withLoggingContext(CONTAINER_NAME_TAG to cname.name) {
                    val containerIsRunning = running.any { it.hasName(cname.name) }
                    checkContainer(cname, containerIsRunning, fixedContainers)
                }
            }
        }

        if (fixedContainers.isEmpty()) {
            logger.info { "Ok" }
        } else {
            logger.info { "Fixed: $fixedContainers" }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "END ($elapsed ms)" }
    }

    private fun checkContainer(cname: ContainerName, containerIsRunning: Boolean, fixedContainers: MutableSet<ContainerName>) {
        val psContainer = postchainContainers()[cname]!!
        val currentResourceLimits = psContainer.resourceLimits
        val updatedResourceLimits = try {
            psContainer.updateResourceLimits()
        } catch (e: UserMistake) {
            logger.warn { "Unable to fetch current container limits for container '${psContainer.containerName.directoryContainer}' from directory chain: ${e.message}" }
            false
        }
        val chainsToTerminate = if (updatedResourceLimits) {
            logger.info { "Resource limits for container ${cname.name} have been changed from $currentResourceLimits to ${psContainer.resourceLimits} and will be restarted" }
            fixedContainers.add(cname)
            psContainer.reset()
            if (containerIsRunning) dockerClient.stopContainer(cname.name, 10)
            dockerClient.removeContainer(cname.name)
            psContainer.getAllChains().toSet()
        } else if (!containerIsRunning) {
            logger.warn { "Subnode container is not running and will be restarted: ${cname.name}" }
            fixedContainers.add(cname)
            psContainer.getAllChains().toSet()
        } else if (!psContainer.isSubnodeHealthy()) {
            logger.warn { "Subnode container is unhealthy and will be restarted: ${cname.name}" }
            fixedContainers.add(cname)
            dockerClient.stopContainer(cname.name, 10)
            psContainer.getAllChains().toSet()
        } else if (!psContainer.checkResourceLimits(fileSystem)) {
            logger.warn { "Subnode container has reached resource limits and will be restarted: ${cname.name}" }
            fixedContainers.add(cname)
            psContainer.reset()
            if (containerIsRunning) dockerClient.stopContainer(cname.name, 10)
            dockerClient.removeContainer(cname.name)
            psContainer.getAllChains().toSet()
        } else {
            logger.debug { "Subnode container is running and healthy: ${cname.name}" }
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
