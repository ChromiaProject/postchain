package net.postchain.containers.bpm

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerState.RUNNING
import net.postchain.containers.bpm.ContainerState.STARTING
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.BlockQueries
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedBlockchainProcessManager

open class ContainerManagedBlockchainProcessManager(
        private val masterBlockchainInfra: MasterBlockchainInfra,
        nodeConfigProvider: NodeConfigurationProvider,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : ManagedBlockchainProcessManager(
        masterBlockchainInfra,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext
) {

    companion object : KLogging()

    /**
     * TODO: [POS-129]: Implement handling of DockerException
     */
    private val containerInitializer = DefaultContainerInitializer(nodeConfig)

    // Docker-In-Docker attempts:
    // DockerClient.builder().uri("http://172.26.32.1:2375") // .uri("unix:///var/run/docker.sock")
    private val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
    private val postchainContainers = mutableSetOf<PostchainContainer>()
    private val lock = Any()

    override fun createDataSource(blockQueries: BlockQueries) =
            BaseDirectoryDataSource(blockQueries, nodeConfigProvider.getConfiguration())

    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid? {
        return if (chainId == CHAIN0) {
            super.startBlockchain(chainId, bTrace)
        } else {
            synchronized(lock) {
                if (isRunning(chainId)) {
                    logger.info(m("startBlockchain: Chain $chainId is stopping"))
                    stopContainerChain(chainId)
                    logger.info(m("startBlockchain: Chain $chainId stopped"))
                }

                logger.info(m("startBlockchain: Chain $chainId is starting up"))
                val brid = startContainerChain(chainId)
                logger.info(m("startBlockchain: Chain $chainId is running"))

                brid
            }
        }
    }

    override fun stopBlockchain(chainId: Long, bTrace: BlockTrace?, restart: Boolean) {
        if (chainId == CHAIN0) {
            super.stopBlockchain(chainId, bTrace, restart)
        } else {
            synchronized(lock) {
                if (isRunning(chainId)) {
                    logger.info(m("stopBlockchain: Chain $chainId is stopping"))
                    stopContainerChain(chainId)
                    logger.info(m("stopBlockchain: Chain $chainId stopped"))
                } else {
                    logger.warn(m("stopBlockchain: Chain $chainId (already) stopped"))
                }
            }
        }
    }

    private fun startContainerChain(chainId: Long): BlockchainRid? {
        val (ds, brid, containerName) = containerNamesFromDataSource(chainId)

        return try {
            // Creating working dir
            val containerChainDir = containerInitializer.createContainerChainWorkingDir(chainId, containerName)

            //Create container (with containerBlockchainProcess) if not exist. Also transfer configs
            val dockerContainer = findDockerContainer(containerName)
            if (dockerContainer != null) {
                logger.info(m("startContainerChain: Container $containerName already exists: ${dockerContainer.id()}"))
                //container exist, but chainID is new. Does postchainContainer exist? If not, add it:
                val container = findContainer(containerName)
                        ?: createPostchainContainer(chainId, ds, containerChainDir, containerName, true)
                                .also { postchainContainers.add(it) }

                if (!containerBlockchainProcessExists(container, chainId)) {
                    val containerBlockchainProcess = createBlockchainProcess(chainId, ds, containerChainDir)

                    // Creating chain configs
                    containerBlockchainProcess.transferConfigsToContainer()
                    container.processes.add(containerBlockchainProcess)
                    if (container.processes.size > 1) {
                        dockerClient.restartContainer(dockerContainer.id())
                    }
                }
            } else { //new docker container, new postchainContainer, new chainID
                logger.info(m("startContainerChain: Container $containerName for chainId $chainId is being created"))
                val newPostchainContainer = createPostchainContainer(
                        chainId, ds, containerChainDir, containerName, false)

                // Creating nodeConfig and peersConfig for container node (node specific)
                containerInitializer.createContainerNodeConfig(newPostchainContainer, containerChainDir)
                containerInitializer.createPeersConfig(newPostchainContainer, containerChainDir)

                postchainContainers.add(newPostchainContainer)
                logger.info(m("startContainerChain: Container $containerName has been created: ${newPostchainContainer.containerId}"))
            }
            // Start docker container if it is not already running
            startContainer(containerName)
            brid
        } catch (e: Exception) {
            // TODO: [POS-129]: Improve error handling/logging
            logger.info("", e)
            val currentContainer = findContainer(containerName)
            currentContainer?.stop()
            postchainContainers.remove(currentContainer)
            null
        }
    }

    private fun containerNamesFromDataSource(chainId: Long): Triple<DirectoryDataSource, BlockchainRid, ContainerName> {
        val ds = dataSource as DirectoryDataSource
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val containerName = ContainerName.create(nodeConfig, ds.getContainerForBlockchain(brid))

        return Triple(ds, brid, containerName)
    }

    private fun containerBlockchainProcessExists(currentContainer: PostchainContainer, chainId: Long): Boolean {
        return currentContainer.processes.any { it.chainId == chainId }
    }

    // Blockchain process is removed, but container is only stopped if it is empty. It might hold other bcs.
    private fun stopContainerChain(chainId: Long) {
        // Remove process from postchainContainer.
        val (_, _, containerName) = containerNamesFromDataSource(chainId)

        val container = findContainer(containerName)
        if (container != null) {
            val process = findProcess(container, chainId)
            if (process != null) {
                heartbeatManager.removeListener(process)
                container.processes.remove(process)
                containerInitializer.killContainerChainWorkingDir(chainId, containerName) // TODO: [POS-164]: Redesign
            }

            // If container now has no BCProcesses left, stop docker container.
            if (container.processes.isEmpty()) {
                container.stop()
                logger.info(m("stopContainerChain: Container $containerName is stopping"))
                dockerClient.stopContainer(container.containerId, 10)
                logger.info(m("stopContainerChain: Container $containerName stopped"))
            } else {
                dockerClient.restartContainer(container.containerId)
                logger.info(m("stopContainerChain: Chain $chainId stopped but container $containerName not empty => container not stopped"))
            }

        } else {
            logger.error(m("stopContainerChain: Can't stop container ${containerName}, does not exist"))
        }
    }

    override fun shutdown() {
        startingOrRunningProcesses()
                .forEach { stopBlockchain(it, bTrace = null) }
        super.shutdown()
    }

    private fun logChain(caller: String, process: ContainerBlockchainProcess, cont: PostchainContainer) {
        val message = "$caller: " +
                "chainId: ${process.chainId}, " +
                "blockchainRid: ${process.blockchainRid}, " +
                "containerName: ${cont.containerName}, " +
                "containerId: ${cont.containerId}"
        logger.info(m(message))
    }

    // Just for tests
    private fun m(message: String) = "\t" + message

    private fun createPostchainContainer(
            chainId: Long,
            dataSource: DirectoryDataSource,
            containerChainDir: ContainerChainDir,
            containerName: ContainerName,
            dockerContainerExists: Boolean
    ): PostchainContainer {

        val process = createBlockchainProcess(chainId, dataSource, containerChainDir)
        val container = DefaultPostchainContainer(
                nodeConfig, dataSource, containerName, mutableSetOf(process), STARTING)
        // Creating chain configs
        process.transferConfigsToContainer()
        container.containerId = if (!dockerContainerExists) {
            val config = ContainerConfigFactory.createConfig(nodeConfig, container, containerChainDir)
            dockerClient.createContainer(config, containerName.toString()).id()!!
        } else {
            findDockerContainer(containerName)!!.id()
        }

        // TODO: [POS-129]: Handle the case when containerId is null
        return container
    }

    private fun createBlockchainProcess(chainId: Long, dataSource: DirectoryDataSource, containerChainDir: ContainerChainDir): ContainerBlockchainProcess {
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val processName = BlockchainProcessName(nodeConfig.pubKey, brid)
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                processName, chainId, brid, dataSource, containerChainDir, nodeConfig.subnodeRestApiPort)
        heartbeatManager.addListener(process)
        return process
    }

    //    f [ "$( docker container inspect -f '{{.State.Running}}' $container_name )" == "true" ]; then ...
    private fun startContainer(containerName: ContainerName) {
        val container = findContainer(containerName)
        val info = dockerClient.inspectContainer(container!!.containerId)
        if (!info.state().running()) {
            logger.info(m("startContainer: Container $containerName is starting up"))
            dockerClient.startContainer(container.containerId)
            container.start()
        }
        logger.info(m("startContainer: Container $containerName is running"))
    }

    private fun isRunning(chainId: Long): Boolean =
            postchainContainers.any { it.contains(chainId) }

    private fun findDockerContainer(containerName: ContainerName): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.find { it.names()?.contains("/$containerName") ?: false } // Prefix /
    }

    private fun findContainer(containerName: ContainerName) =
            postchainContainers.find { it.containerName == containerName }

    private fun findProcess(container: PostchainContainer, chainId: Long): ContainerBlockchainProcess? =
            container.processes.find { it.chainId == chainId }

    override fun getLaunchedBlockchains(): Set<Long> {
        return super.getLaunchedBlockchains() + startingOrRunningProcesses()
    }

    private fun startingOrRunningProcesses(): Set<Long> {
        return postchainContainers
                .filter { it.state == STARTING || it.state == RUNNING }
                .flatMap { it.getChains() }
                .toSet()
    }
}
