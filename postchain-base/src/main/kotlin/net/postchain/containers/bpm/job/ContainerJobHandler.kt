package net.postchain.containers.bpm.job

import mu.KLogging
import mu.withLoggingContext
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerConfigFactory
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerState
import net.postchain.containers.bpm.DefaultPostchainContainer
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.containers.bpm.docker.DockerTools.findHostPorts
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.crypto.PrivKey
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.CONTAINER_NAME_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import net.postchain.managed.DirectoryDataSource
import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.messages.Container
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class ContainerJobHandler(
        private val appConfig: AppConfig,
        private val nodeDiagnosticContext: NodeDiagnosticContext,
        private val nodeName: String,
        private val dockerClient: DockerClient,
        private val directoryDataSource: () -> DirectoryDataSource,
        private val postchainContainers: () -> MutableMap<ContainerName, PostchainContainer>,
        private val terminateBlockchainProcess: (Long, PostchainContainer) -> ContainerBlockchainProcess?,
        private val createBlockchainProcess: (Chain, PostchainContainer) -> ContainerBlockchainProcess?
) {

    companion object : KLogging() {
        private const val SCOPE = "ContainerJobHandler"
    }

    private val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
    private val fs = FileSystem.create(containerNodeConfig)

    fun handleJob(job: ContainerJob) {
        withLoggingContext(NODE_PUBKEY_TAG to appConfig.pubKey, CONTAINER_NAME_TAG to job.containerName.name) {
            handleJobInternal(job)
        }
    }

    private fun handleJobInternal(job: ContainerJob) {
        val containerName = job.containerName
        logger.info {
            "[${nodeName}]: $SCOPE -- Job for container will be handled: " +
                    "containerName: ${containerName}, " +
                    "chains to stop: ${job.chainsToStop.map { it.chainId }.toTypedArray().contentToString()}, " +
                    "chains to start: ${job.chainsToStart.map { it.chainId }.toTypedArray().contentToString()}"
        }

        fun result(result: Boolean) {
            val msg = when (result) {
                true -> "Job for container $containerName has been finished successfully"
                false -> "Job for container $containerName hasn't been finished yet and will be postponed"
            }
            logger.info { "[${nodeName}]: $SCOPE -- $msg" }
        }

        // 1. Create PostchainContainer
        val psContainer = ensurePostchainContainer(containerName) ?: return result(false)

        // 2. Start Docker container
        val dockerContainer = findDockerContainer(job.containerName)
        if (dockerContainer == null && job.chainsToStart.isNotEmpty()) {
            startDockerContainer(containerName, psContainer, job)
            return result(false)
        }

        // 3. Assert subnode is connected and running
        if (dockerContainer != null && job.chainsToStart.isNotEmpty()) {
            if (!ensureSubNode(psContainer, dockerContainer, containerName, job)) return result(false)
        } else {
            logger.debug { "[${nodeName}]: $SCOPE -- DockerContainer is not running, 'is subnode connected' check will be skipped, container: ${job.containerName}" }
        }

        // 4. Stop chains
        stopChains(job, psContainer)

        // 5. Start chains
        startChains(job, psContainer)

        // 6. Stop container if it is empty
        stopContainerIfEmpty(job, psContainer, containerName, dockerContainer)

        job.done = true
        return result(true)
    }

    private fun ensureSubNode(psContainer: PostchainContainer, dockerContainer: Container, containerName: ContainerName, job: ContainerJob): Boolean {
        psContainer.containerId = dockerContainer.id()
        val dcState = dockerContainer.state()
        if (dcState in listOf("exited", "created", "paused")) {
            logger.info { dcLog(containerName, "$dcState and will be started", psContainer) }
            updateResourceLimits(psContainer)
            dockerClient.startContainer(dockerContainer.id())

            // We may have new ports so let's ensure we re-connect with those
            if (psContainer.state == ContainerState.RUNNING) psContainer.reset()
            logger.info { dcLog(containerName, "restarted", psContainer) }

            job.postponeWithBackoff()
            return false
        }
        if (psContainer.state != ContainerState.RUNNING) {
            psContainer.containerPortMapping.putAll(dockerClient.findHostPorts(dockerContainer.id(), containerNodeConfig.subnodePorts))
            psContainer.start()
            job.postpone(5_000)
            return false
        }
        if (!psContainer.initializePostchainNode(PrivKey(appConfig.privKey))) {
            logger.warn { "[${nodeName}]: $SCOPE -- Failed to initialize Postchain node, container: ${containerName}" }
            job.postpone(5_000)
            return false
        }
        if (!psContainer.isSubnodeHealthy()) {
            logger.warn { "[${nodeName}]: $SCOPE -- Subnode is unhealthy, container: ${containerName}" }
            job.postpone(5_000)
            return false
        }
        job.resetFailedStartCount()
        logger.info { "[${nodeName}]: $SCOPE -- Subnode is healthy, container: ${containerName}" }
        return true
    }

    private fun stopContainerIfEmpty(job: ContainerJob, psContainer: PostchainContainer, containerName: ContainerName, dockerContainer: Container?) {
        if (job.chainsToStart.isEmpty() && psContainer.isEmpty()) {
            logger.info { "[${nodeName}]: $SCOPE -- Container is empty and will be stopped: $containerName" }
            psContainer.stop()
            postchainContainers().remove(psContainer.containerName)
            if (dockerContainer != null) {
                dockerClient.stopContainer(dockerContainer.id(), 10)
                logger.debug { "[${nodeName}]: $SCOPE -- Docker container stopped: $containerName" }
            }
            logger.info { "[${nodeName}]: $SCOPE -- Container stopped: $containerName" }
        }
    }

    private fun startChains(job: ContainerJob, psContainer: PostchainContainer) {
        job.chainsToStart.forEach { chain ->
            withLoggingContext(CHAIN_IID_TAG to chain.chainId.toString(), BLOCKCHAIN_RID_TAG to chain.brid.toHex()) {
                val process = createBlockchainProcess(chain, psContainer)
                logger.debug { "[${nodeName}]: $SCOPE -- ContainerBlockchainProcess created: $process" }
                if (process == null) {
                    logger.error { "[${nodeName}]: $SCOPE -- Blockchain didn't start: ${chain.chainId} / ${chain.brid.toShortHex()} " }
                } else {
                    logger.info { "[${nodeName}]: $SCOPE -- Blockchain started: ${chain.chainId} / ${chain.brid.toShortHex()} " }
                }
            }
        }
    }

    private fun stopChains(job: ContainerJob, psContainer: PostchainContainer) {
        job.chainsToStop.forEach { chain ->
            val process = terminateBlockchainProcess(chain.chainId, psContainer)
            withLoggingContext(CHAIN_IID_TAG to chain.chainId.toString(), BLOCKCHAIN_RID_TAG to chain.brid.toHex()) {
                logger.debug { "[${nodeName}]: $SCOPE -- ContainerBlockchainProcess terminated: $process" }
                logger.info { "[${nodeName}]: $SCOPE -- Blockchain stopped: ${chain.chainId} / ${chain.brid.toShortHex()} " }
            }
        }
    }

    private fun startDockerContainer(containerName: ContainerName, psContainer: PostchainContainer, job: ContainerJob) {
        logger.debug { dcLog(containerName, "not found", null) }
        updateResourceLimits(psContainer)
        val containerId = createDockerContainer(psContainer, containerName)
        dockerClient.startContainer(containerId)
        psContainer.containerId = containerId
        logger.info { dcLog(containerName, "started", psContainer) }
        job.postpone(1_000)
    }

    private fun updateResourceLimits(psContainer: PostchainContainer) {
        psContainer.updateResourceLimits()
        fs.applyLimits(psContainer.containerName, psContainer.resourceLimits)
    }

    private fun createDockerContainer(psContainer: PostchainContainer, containerName: ContainerName): String {
        val config = ContainerConfigFactory.createConfig(fs, appConfig, containerNodeConfig, psContainer)
        return dockerClient.createContainer(config, containerName.toString()).id()!!.also {
            logger.debug { dcLog(containerName, "created", psContainer) }
        }
    }

    private fun findDockerContainer(containerName: ContainerName): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.firstOrNull { it.hasName(containerName.name) }
    }

    private fun ensurePostchainContainer(containerName: ContainerName): PostchainContainer? {
        val psContainer = postchainContainers()[containerName]
        if (psContainer != null) return psContainer

        logger.debug { "[${nodeName}]: $SCOPE -- PostchainContainer not found and will be created" }
        val newContainer = createPostchainContainer(containerName)
        logger.debug { "[${nodeName}]: $SCOPE -- PostchainContainer created" }
        val dir = initContainerWorkingDir(fs, newContainer)
        return if (dir != null) {
            postchainContainers()[newContainer.containerName] = newContainer
            logger.debug { "[${nodeName}]: $SCOPE -- Container dir initialized, container: ${containerName}, dir: $dir" }
            newContainer
        } else {
            logger.error { "[${nodeName}]: $SCOPE -- Container dir hasn't been initialized, container: $containerName" }
            null
        }
    }

    private fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path? =
            fs.createContainerRoot(container.containerName, container.resourceLimits)

    private fun createPostchainContainer(containerName: ContainerName): PostchainContainer {
        val containerPortMapping = ConcurrentHashMap<Int, Int>()
        val subnodeAdminClient = SubnodeAdminClient.create(containerNodeConfig, containerPortMapping, nodeDiagnosticContext)
        return DefaultPostchainContainer(directoryDataSource(), containerName, containerPortMapping, ContainerState.STARTING, subnodeAdminClient)
    }

    private fun dcLog(containerName: ContainerName, state: String, container: PostchainContainer?) =
            "[${nodeName}]: $SCOPE -- Docker container $state: ${containerName}, " +
                    "containerId: ${container?.shortContainerId()}"
}