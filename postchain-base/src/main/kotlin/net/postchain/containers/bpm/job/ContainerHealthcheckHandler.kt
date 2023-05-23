package net.postchain.containers.bpm.job

import mu.KLogging
import mu.withLoggingContext
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.logging.CONTAINER_NAME_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import org.mandas.docker.client.DockerClient

class ContainerHealthcheckHandler(
        private val appConfig: AppConfig,
        private val nodeName: String,
        private val dockerClient: DockerClient,
        private val postchainContainers: () -> MutableMap<ContainerName, PostchainContainer>,
        private val removeBlockchainProcess: (Long, PostchainContainer) -> ContainerBlockchainProcess?
) {

    companion object : KLogging() {
        private const val SCOPE = "ContainerHealthcheckHandler"
    }

    fun check(containersInProgress: Set<String>) {
        withLoggingContext(NODE_PUBKEY_TAG to appConfig.pubKey) {
            checkInternal(containersInProgress)
        }
    }

    private fun checkInternal(containersInProgress: Set<String>) {
        val start = System.currentTimeMillis()
        logger.debug { "[${nodeName}]: $SCOPE -- BEGIN" }

        val containersToCheck = postchainContainers().keys
                .associateBy { it.name }
                .filter { it.key !in containersInProgress }

        logger.info {
            "[${nodeName}]: $SCOPE -- containersInProgress: $containersInProgress, containersToCheck: ${containersToCheck.keys}"
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
            logger.info { "[${nodeName}]: $SCOPE -- Ok" }
        } else {
            logger.info { "[${nodeName}]: $SCOPE -- Fixed: $fixedContainers" }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "[${nodeName}]: $SCOPE -- END ($elapsed ms)" }
    }

    private fun checkContainer(cname: ContainerName, containerIsRunning: Boolean, fixedContainers: MutableSet<ContainerName>) {
        val psContainer = postchainContainers()[cname]!!
        val currentResourceLimits = psContainer.resourceLimits
        val chainsToTerminate = if (psContainer.updateResourceLimits()) {
            logger.info { "[${nodeName}]: $SCOPE -- Resource limits for container ${cname.name} have been changed from $currentResourceLimits to ${psContainer.resourceLimits} and will be restarted" }
            fixedContainers.add(cname)
            psContainer.reset()
            if (containerIsRunning) dockerClient.stopContainer(cname.name, 10)
            dockerClient.removeContainer(cname.name)
            psContainer.getAllChains().toSet()
        } else if (!containerIsRunning) {
            logger.warn { "[${nodeName}]: $SCOPE -- Subnode container is not running and will be restarted: ${cname.name}" }
            fixedContainers.add(cname)
            psContainer.getAllChains().toSet()
        } else if (!psContainer.isSubnodeHealthy()) {
            logger.warn { "[${nodeName}]: $SCOPE -- Subnode container is unhealthy and will be restarted: ${cname.name}" }
            fixedContainers.add(cname)
            dockerClient.stopContainer(cname.name, 10)
            psContainer.getAllChains().toSet()
        } else {
            logger.debug { "[${nodeName}]: $SCOPE -- Subnode container is running and healthy: ${cname.name}" }
            psContainer.getStoppedChains().toSet()
        }

        chainsToTerminate.forEach {
            removeBlockchainProcess(it, psContainer)
        }
        if (chainsToTerminate.isNotEmpty()) {
            logger.info { "[${nodeName}]: $SCOPE -- Container chains have been terminated: $chainsToTerminate" }
        }
    }
}