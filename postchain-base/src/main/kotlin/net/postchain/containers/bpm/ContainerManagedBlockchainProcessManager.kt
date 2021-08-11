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
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.BlockQueries
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedBlockchainProcessManager
import java.nio.file.Path

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

    private fun chainIdInContainers(chainId: Long): Boolean {
        return postchainContainers.any { it.contains(chainId) }
    }

    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid? {
        return if (chainId == CHAIN0) {
            super.startBlockchain(chainId, bTrace)
        } else {
            synchronized(lock) {
                if (chainIdInContainers(chainId)) {
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
                if (chainIdInContainers(chainId)) {
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
        val (ds, brid, containerNames) = containerNamesFromDataSource(chainId)
        if (containerNames["directory"] == null) return null
        val nodeContainerName = containerNames["node"]!! //if directoryContainerName is non-null, so is nodeContainerName

        return try {
            // Creating working dir
            val (containerDir, chainConfigsDir) = containerInitializer.createContainerChainWorkingDir(chainId, nodeContainerName)

            //Create container (with containerBlockchainProcess) if not exist. Also transfer configs
            val dockerContainer = findDockerContainer(nodeContainerName)
            if (dockerContainer != null) {
                logger.info(m("startContainerChain: Container $nodeContainerName already exists: ${dockerContainer.id()}"))
                //container exist, but chainID is new. Does postchainContainer exist? If not, add it:
                val container = findContainer(nodeContainerName)
                        ?: createPostchainContainer(
                                chainId, ds, containerDir, chainConfigsDir, containerNames, true
                        ).also { postchainContainers.add(it) }

                if (!containerBlockchainProcessExists(container, chainId)) {
                    val containerBlockchainProcess = createBlockchainProcess(chainId, ds, chainConfigsDir)

                    // Creating chain configs
                    containerBlockchainProcess.transferConfigsToContainer()
                    container.blockchainProcesses.add(containerBlockchainProcess)
                    if (container.blockchainProcesses.size > 1) {
                        dockerClient.restartContainer(dockerContainer.id())
                    }
                }
            } else { //new docker container, new postchainContainer, new chainID
                logger.info(m("startContainerChain: Container $nodeContainerName for chainId $chainId is being created"))
                val newPostchainContainer = createPostchainContainer(
                        chainId, ds,
                        containerDir, chainConfigsDir, containerNames, false
                )
                // Creating nodeConfig and peersConfig for container node (node specific)
                containerInitializer.createContainerNodeConfig(newPostchainContainer, containerDir)
                containerInitializer.createPeersConfig(newPostchainContainer, containerDir)

                postchainContainers.add(newPostchainContainer)
                logger.info(m("startContainerChain: Container $nodeContainerName has been created: ${newPostchainContainer.containerId}"))
            }
            // Start docker container if it is not already running
            startContainer(nodeContainerName)
            brid
        } catch (e: Exception) {
            // TODO: [POS-129]: Improve error handling/logging
            logger.info("", e)
            val currentContainer = findContainer(nodeContainerName)
            currentContainer?.stop()
            postchainContainers.remove(currentContainer)
            null
        }
    }

    private fun containerNamesFromDataSource(chainId: Long): Triple<DirectoryDataSource, BlockchainRid, Map<String, String?>> {
        val ds = dataSource as DirectoryDataSource
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val directoryContainerName = ds.getContainerForBlockchain(brid)
        val nodeContainerName = "${directoryContainerName}_${nodeConfig.pubKey.take(8)}"
        val containerName = mapOf("directory" to directoryContainerName, "node" to nodeContainerName)
        return Triple(ds, brid, containerName)
    }

    private fun containerBlockchainProcessExists(currentContainer: PostchainContainer, chainId: Long): Boolean {
        return currentContainer.blockchainProcesses.any { it.chainId == chainId }
    }

    // Blockchain process is removed, but container is only stopped if it is empty. It might hold other bcs.
    private fun stopContainerChain(chainId: Long) {
        // Remove process from postchainContainer.
        val (_, _, containerName) = containerNamesFromDataSource(chainId)
        val nodeContainerName = containerName["node"]!!

        val container = findContainer(nodeContainerName)
        if (container != null) {
            val process = findProcess(container, chainId)
            if (process != null) {
                heartbeatManager.removeListener(process)
                container.blockchainProcesses.remove(process)
                containerInitializer.killContainerChainWorkingDir(chainId, nodeContainerName) // TODO: [POS-164]: Redesign
            }

            // If container now has no BCProcesses left, stop docker container.
            if (container.blockchainProcesses.isEmpty()) {
                container.stop()
                logger.info(m("stopContainerChain: Container $nodeContainerName is stopping"))
                dockerClient.stopContainer(container.containerId, 10)
                logger.info(m("stopContainerChain: Container $nodeContainerName stopped"))
            } else {
                dockerClient.restartContainer(container.containerId)
                logger.info(m("stopContainerChain: Chain $chainId stopped but container $nodeContainerName not empty => container not stopped"))
            }

        } else {
            logger.error(m("stopContainerChain: Can't stop container ${nodeContainerName}, does not exist"))
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
                "containerName: ${cont.nodeContainerName}, " +
                "containerId: ${cont.containerId}"
        logger.info(m(message))
    }

    // Just for tests
    private fun m(message: String) = "\t" + message

    private fun createPostchainContainer(
            chainId: Long,
            dataSource: DirectoryDataSource,
            containerDir: Path,
            chainConfigsDir: Path,
            containerNames: Map<String, String?>,
            dockerContainerExists: Boolean
    ): PostchainContainer {

        val containerBlockchainProcess = createBlockchainProcess(chainId, dataSource, chainConfigsDir)

        val postchainContainer = DefaultPostchainContainer(
                nodeConfig, mutableSetOf(containerBlockchainProcess), dataSource, ContainerState.STARTING, containerNames)

        // Creating chain configs
        containerBlockchainProcess.transferConfigsToContainer()

        if (!dockerContainerExists) {
            val config = ContainerConfigFactory.createConfig(nodeConfig, postchainContainer, containerDir)
            postchainContainer.containerId = dockerClient.createContainer(config, containerNames["node"]).id().toString()
        } else {
            postchainContainer.containerId = findDockerContainer(containerNames["node"]!!)!!.id()
        }
        // TODO: [POS-129]: Handle the case when containerId is null
        return postchainContainer
    }

    private fun createBlockchainProcess(chainId: Long, dataSource: DirectoryDataSource, chainConfigsDir: Path): ContainerBlockchainProcess {
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val processName = BlockchainProcessName(nodeConfig.pubKey, brid)
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                processName, chainId, brid, dataSource, chainConfigsDir, nodeConfig.subnodeRestApiPort)
        heartbeatManager.addListener(process)
        return process
    }

    private fun findDockerContainer(containerName: String): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.find { it.names()?.contains("/$containerName") ?: false } // Prefix /
    }

    //    f [ "$( docker container inspect -f '{{.State.Running}}' $container_name )" == "true" ]; then ...
    private fun startContainer(nodeContainerName: String) {
        val currentContainer = findContainer(nodeContainerName)
        val h = dockerClient.inspectContainer(currentContainer!!.containerId)
        if (!h.state().running()) {
            logger.info(m("startContainer: Container $nodeContainerName is starting up"))
            dockerClient.startContainer(currentContainer.containerId)
            currentContainer.start()
        }
        logger.info(m("startContainer: Container $nodeContainerName is running"))
    }

    private fun findContainer(containerName: String) =
            postchainContainers.find { it.nodeContainerName == containerName }

    private fun findProcess(container: PostchainContainer, chainId: Long): ContainerBlockchainProcess? =
            container.blockchainProcesses.find { it.chainId == chainId }

    override fun getLaunchedBlockchains(): Set<Long> {
        return super.getLaunchedBlockchains() + startingOrRunningProcesses()
    }

    private fun startingOrRunningProcesses(): Set<Long> {
        val activeContainers = postchainContainers.filter {
            it.state == ContainerState.STARTING || it.state == ContainerState.RUNNING
        }
        return activeContainers.flatMap { it.getChains() }.toSet()
    }
}
