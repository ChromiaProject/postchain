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
        postchainContainers.forEach { if (it.contains(chainId)) return true }
        return false
    }

    override fun startBlockchain(chainId: Long): BlockchainRid? {
        return if (chainId == CHAIN0) {
            super.startBlockchain(chainId)
        } else {
            synchronized(lock) {
                if (chainIdInContainers(chainId)) {
                    logger.info(m("startBlockchain: Container chain $chainId is stopping"))
                    stopContainerChain(chainId)
                    logger.info(m("startBlockchain: Container chain $chainId stopped"))
                }

                logger.info(m("startBlockchain: Container chain $chainId is starting up"))
                val brid = startContainerChain(chainId)
                logger.info(m("startBlockchain: Container chain $chainId is running"))

                brid
            }
        }
    }

    override fun stopBlockchain(chainId: Long) {
        if (chainId == CHAIN0) {
            super.stopBlockchain(chainId)
        } else {
            synchronized(lock) {
                if (chainIdInContainers(chainId)) {
                    logger.info(m("stopBlockchain: Container chain $chainId is stopping"))
                    stopContainerChain(chainId)
                    logger.info(m("stopBlockchain: Container chain $chainId stopped"))
                } else {
                    logger.warn(m("stopBlockchain: Container chain $chainId (already) stopped"))
                }
            }
        }
    }

    private fun startContainerChain(chainId: Long): BlockchainRid? {
        val (ds, brid, containerName) = containerNameFromDataSource(chainId)
        if (containerName == null) return null

        return try {
            // Creating working dir
            val (containerDir, chainConfigsDir) = containerInitializer.createContainerWorkingDir(chainId, containerName)

            //Create container (with containerBlockchainProcess) if not exist. Also transfer configs
            val container = containerName.let { findDockerContainer(it) }
            if (container != null) {
                logger.info(m("startContainer: Container $containerName already exists: ${container.id()}"))
                //container exist, but chainID is new. Does postchainContainer exist? If not, add it:
                val existingPostchainContainer = getPostchainContainer(containerName)
                val currentContainer: PostchainContainer
                if (existingPostchainContainer != null) {
                    currentContainer = existingPostchainContainer
                } else {
                    currentContainer = createPostchainContainer(
                            chainId, ds,
                            containerDir, chainConfigsDir, containerName, true
                    )
                    postchainContainers.add(currentContainer)
                }
                if (!containerBlockchainProcessExists(currentContainer, chainId)) {
                    val containerBlockchainProcess = createBlockchainProcess(chainId, ds, chainConfigsDir)

                    // Creating chain configs
                    containerBlockchainProcess.transferConfigsToContainer()
                    currentContainer.blockchainProcesses.add(containerBlockchainProcess)
                }
            } else { //new docker container, new postchainContainer, new chainID
                logger.info(m("startContainer: Container $containerName is being created"))
                val newPostchainContainer = createPostchainContainer(
                        chainId, ds,
                        containerDir, chainConfigsDir, containerName, false
                )
                // Creating nodeConfig for container node
                containerInitializer.createContainerNodeConfig(newPostchainContainer, containerDir)

                postchainContainers.add(newPostchainContainer)
                logger.info(m("startContainer: Container $containerName has been created: ${newPostchainContainer.containerId}"))
            }
            //Start docker container if it is not already running
            startContainer(containerName)
            brid
        } catch (e: Exception) {
            // TODO: [POS-129]: Improve error handling/logging
            logger.info("", e)
            val currentContainer = getPostchainContainer(containerName)
            if (currentContainer != null) {
                currentContainer.stop()
            }
            postchainContainers.remove(currentContainer)
            null
        }
    }

    private fun containerNameFromDataSource(chainId: Long): Triple<DirectoryDataSource, BlockchainRid, String?> {
        val ds = dataSource as DirectoryDataSource
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val containerName = ds.getContainerForBlockchain(brid)
        return Triple(ds, brid, containerName)
    }

    private fun containerBlockchainProcessExists(currentContainer: PostchainContainer, chainId: Long): Boolean {
        return currentContainer.blockchainProcesses.any { it.chainId == chainId }
    }

    //Blockchain process is removed, but container is only stopped if it is empty. It might hold other bcs.
    private fun stopContainerChain(chainId: Long) {
        //remove process from postchainContainer.
        val (_, _, containerName) = containerNameFromDataSource(chainId)
        val currentContainer = getPostchainContainer(containerName)
        if (currentContainer != null) {
            currentContainer.blockchainProcesses.removeIf { it.chainId == chainId }
            //If container now has no BCProcesses left, stop docker container.
            if (currentContainer.blockchainProcesses.isEmpty()) {
                currentContainer.stop()
                logger.info(m("stopContainerChain: Container $containerName is stopping"))
                dockerClient.stopContainer(currentContainer.containerId, 10)
                logger.info(m("stopContainerChain: Container $containerName stopped"))
            } else
                logger.info(m("stopContainerChain: Blockchain $chainId stopped but container $containerName not empty ( container => not stopped)"))

        } else {
            logger.error(m("stopContainerChain: Can't stop container ${containerName}, does not exist"))
        }
    }

    override fun shutdown() {
        startingOrRunningProcesses()
                .forEach { stopBlockchain(it) }
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

    private fun createPostchainContainer(chainId: Long, dataSource: DirectoryDataSource, containerDir: Path,
                                         chainConfigsDir: Path,
                                         containerName: String,
                                         dockerContainerExists: Boolean): PostchainContainer {

        val containerBlockchainProcess = createBlockchainProcess(chainId, dataSource, chainConfigsDir)


        val postchainContainer = DefaultPostchainContainer(nodeConfig, mutableSetOf(containerBlockchainProcess), dataSource,
                ContainerState.STARTING, containerName)

        // Creating chain configs
        containerBlockchainProcess.transferConfigsToContainer()

        if (!dockerContainerExists) {
            val config = ContainerConfigFactory.createConfig(nodeConfig, postchainContainer, containerDir)
            postchainContainer.containerId = dockerClient.createContainer(config, containerName).id().toString()
        } else {
            postchainContainer.containerId = findDockerContainer(containerName)!!.id()
        }
        // TODO: [POS-129]: Handle the case when containerId is null
        return postchainContainer
    }


    private fun createBlockchainProcess(chainId: Long, dataSource: DirectoryDataSource, chainConfigsDir: Path): ContainerBlockchainProcess {
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val processName = BlockchainProcessName(nodeConfig.pubKey, brid)
        return masterBlockchainInfra.makeMasterBlockchainProcess(
                processName, chainId, brid, dataSource, chainConfigsDir, nodeConfig.subnodeRestApiPort
        )
    }

    private fun findDockerContainer(containerName: String): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.find { it.names()?.contains("/$containerName") ?: false } // Prefix /
    }

    //    f [ "$( docker container inspect -f '{{.State.Running}}' $container_name )" == "true" ]; then ...
    private fun startContainer(containerName: String) {
        val currentContainer = getPostchainContainer(containerName)
        logger.info(m("startContainerChain: Container ${containerName} is starting up"))

        val h = dockerClient.inspectContainer(currentContainer!!.containerId)
        if (!h.state().running()) {
            dockerClient.startContainer(currentContainer.containerId)
            currentContainer.start()
        }
        logger.info(m("startContainerChain: Container ${containerName} is running"))
    }

    private fun getPostchainContainer(containerName: String?) =
            postchainContainers.find { it.containerName == containerName }

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
