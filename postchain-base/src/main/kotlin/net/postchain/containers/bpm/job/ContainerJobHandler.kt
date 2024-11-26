package net.postchain.containers.bpm.job

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import mu.KLogging
import mu.withLoggingContext
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerHandler
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerState
import net.postchain.containers.bpm.DefaultPostchainContainer
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.bpm.rpc.DefaultSubnodeAdminClient
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.crypto.PrivKey
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.CONTAINER_NAME_TAG
import net.postchain.managed.DirectoryDataSource
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class ContainerJobHandler(
        private val appConfig: AppConfig,
        private val nodeDiagnosticContext: NodeDiagnosticContext,
        dockerClient: DockerClient,
        private val fileSystem: FileSystem,
        private val directoryDataSource: () -> DirectoryDataSource,
        private val postchainContainers: () -> MutableMap<ContainerName, PostchainContainer>,
        private val terminateBlockchainProcess: (Long, PostchainContainer) -> ContainerBlockchainProcess?,
        private val createBlockchainProcess: (Chain, PostchainContainer) -> ContainerBlockchainProcess?
) : ContainerHandler(dockerClient, appConfig, fileSystem) {

    companion object : KLogging() {
        internal fun getDefaultContainerImage(config: ContainerNodeConfig): String =
                when (val expectedTag = config.imageVersionTag) {
                    "" -> config.containerImage
                    else -> {
                        when (val actualTag = config.containerImage.substringAfter(":", "")) {
                            "" -> config.containerImage.substringBefore(":") + ":" + expectedTag
                            else -> {
                                if (expectedTag != actualTag) {
                                    logger.warn { "Container image version tag ($actualTag) is not equal to the environment image version tag ($expectedTag)" }
                                }
                                config.containerImage
                            }
                        }
                    }
                }
    }

    fun handleJob(job: ContainerJob) {
        withLoggingContext(CONTAINER_NAME_TAG to job.containerName.dockerContainer) {
            handleJobInternal(job)
        }
    }

    private fun handleJobInternal(job: ContainerJob) {
        val containerName = job.containerName
        logger.info {
            "Job for container will be handled: " +
                    "containerName: ${containerName}, " +
                    "chains to stop: ${job.chainsToStop.map { it.chainId }.toTypedArray().contentToString()}, " +
                    "chains to start: ${job.chainsToStart.map { it.chainId }.toTypedArray().contentToString()}"
        }

        fun result(result: Boolean) {
            val msg = when (result) {
                true -> "Job for container $containerName has been finished successfully"
                false -> "Job for container $containerName hasn't been finished yet and will be postponed"
            }
            logger.info { msg }
        }

        // 1. Create PostchainContainer
        val psContainer = ensurePostchainContainer(containerName) ?: return result(false)

        // 2. Start Docker container
        val dockerContainer = findContainer(job.containerName.dockerContainer)
        if (dockerContainer == null && job.chainsToStart.isNotEmpty()) {
            startDockerContainer(psContainer, job)
            return result(false)
        }

        // 3. Assert subnode is connected and running
        if (dockerContainer != null && job.chainsToStart.isNotEmpty()) {
            if (!ensureSubNode(psContainer, dockerContainer, job)) return result(false)
        } else {
            logger.debug { "DockerContainer is not running, 'is subnode connected' check will be skipped, container: ${job.containerName}" }
        }

        // 4. Stop chains
        stopChains(job, psContainer)

        // 5. Start chains
        startChains(job, psContainer)

        job.done = true
        return result(true)
    }

    private fun ensureSubNode(psContainer: PostchainContainer, dockerContainer: Container, job: ContainerJob): Boolean {
        psContainer.containerId = dockerContainer.id
        val dcState = dockerContainer.state
        if (dcState in listOf("exited", "created", "paused")) {
            logger.info { dcLog(psContainer.containerName, "$dcState and will be started", psContainer) }
            updateResourceLimits(psContainer)
            startContainer(psContainer)

            // We may have new ports so let's ensure we re-connect with those
            if (psContainer.state == ContainerState.RUNNING) psContainer.reset()
            logger.info { dcLog(psContainer.containerName, "restarted", psContainer) }

            job.postponeWithBackoff()
            return false
        }
        if (psContainer.state != ContainerState.RUNNING) {
            psContainer.containerPortMapping.putAll(findHostPorts(dockerContainer.id, containerNodeConfig.subnodePorts))
            psContainer.start()
            job.postpone(5_000)
            return false
        }
        if (!psContainer.initializePostchainNode(PrivKey(appConfig.privKey))) {
            logger.warn { "Failed to initialize Postchain node, container: ${psContainer.containerName}" }
            job.postpone(5_000)
            return false
        }
        if (!psContainer.isSubnodeHealthy()) {
            logger.warn { "Subnode is unhealthy, container: ${psContainer.containerName}" }
            job.postpone(5_000)
            return false
        }
        job.resetFailedStartCount()
        logger.info { "Subnode is healthy, container: ${psContainer.containerName}" }
        return true
    }

    private fun startChains(job: ContainerJob, psContainer: PostchainContainer) {
        job.chainsToStart.forEach { chain ->
            withLoggingContext(CHAIN_IID_TAG to chain.chainId.toString(), BLOCKCHAIN_RID_TAG to chain.brid.toHex()) {
                val process = createBlockchainProcess(chain, psContainer)
                logger.debug { "ContainerBlockchainProcess created: ${process != null}" }
                if (process == null) {
                    logger.error { "Blockchain didn't start" }
                } else {
                    logger.info { "Blockchain started" }
                }
            }
        }
    }

    private fun stopChains(job: ContainerJob, psContainer: PostchainContainer) {
        job.chainsToStop.forEach { chain ->
            terminateBlockchainProcess(chain.chainId, psContainer)
            withLoggingContext(CHAIN_IID_TAG to chain.chainId.toString(), BLOCKCHAIN_RID_TAG to chain.brid.toHex()) {
                logger.debug { "ContainerBlockchainProcess terminated" }
                logger.info { "Blockchain stopped" }
            }
        }
    }

    private fun startDockerContainer(psContainer: PostchainContainer, job: ContainerJob) {
        logger.debug { dcLog(psContainer.containerName, "not found", null) }
        updateResourceLimits(psContainer)
        psContainer.checkResourceLimits(fileSystem)
        psContainer.updateImage()
        psContainer.containerId = pullAndCreateDockerContainer(psContainer)
        startContainer(psContainer)
        logger.info { dcLog(psContainer.containerName, "started", psContainer) }
        job.postpone(1_000)
    }

    private fun updateResourceLimits(psContainer: PostchainContainer) {
        psContainer.updateResourceLimits()
        fileSystem.applyLimits(psContainer.containerName, psContainer.resourceLimits)
    }

    private fun pullAndCreateDockerContainer(psContainer: PostchainContainer): String {
        val containerImageInfo = psContainer.image
        val image = if (containerImageInfo != null) {
            val imageSpec = "${containerImageInfo.url}@${containerImageInfo.digest}"
            logger.info("Pulling image $imageSpec...")
            pullImage(imageSpec)
            imageSpec
        } else {
            logger.info("Using default image")
            getDefaultContainerImage(containerNodeConfig)
        }
        return createDockerContainer(psContainer.containerName, psContainer.resourceLimits, psContainer.readOnly.get(), image).also {
            logger.debug { dcLog(psContainer.containerName, "created", psContainer) }
        }
    }

    private fun ensurePostchainContainer(containerName: ContainerName): PostchainContainer? {
        val psContainer = postchainContainers()[containerName]
        if (psContainer != null) return psContainer
        logger.debug { "PostchainContainer not found and will be created" }
        val newContainer = createPostchainContainer(containerName)
        logger.debug { "PostchainContainer created" }
        val dir = initContainerWorkingDir(fileSystem, newContainer)
        return if (dir != null) {
            postchainContainers()[newContainer.containerName] = newContainer
            logger.debug { "Container dir initialized, container: ${containerName}, dir: $dir" }
            newContainer
        } else {
            logger.error { "Container dir hasn't been initialized, container: $containerName" }
            null
        }
    }

    private fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path? =
            fs.createContainerRoot(container.containerName, container.resourceLimits)

    private fun createPostchainContainer(containerName: ContainerName): PostchainContainer {
        val containerPortMapping = ConcurrentHashMap<Int, Int>()
        val subnodeAdminClient = DefaultSubnodeAdminClient(containerName, containerNodeConfig, containerPortMapping, nodeDiagnosticContext)
        return DefaultPostchainContainer(containerNodeConfig, directoryDataSource(), containerName, containerPortMapping, ContainerState.STARTING, subnodeAdminClient)
    }

    private fun dcLog(containerName: ContainerName, state: String, container: PostchainContainer?) =
            "Docker container $state: ${containerName}, " +
                    "containerId: ${container?.shortContainerId()}"
}