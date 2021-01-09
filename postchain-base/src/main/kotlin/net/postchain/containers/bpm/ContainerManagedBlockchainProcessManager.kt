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
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedBlockchainProcessManager
import java.util.concurrent.atomic.AtomicInteger

class ContainerManagedBlockchainProcessManager(
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
    private val containerProcesses = mutableMapOf<Long, ContainerBlockchainProcess>()
    private val lock = Any()

    override fun startBlockchain(chainId: Long): BlockchainRid? {
        return if (chainId == CHAIN0) {
            super.startBlockchain(chainId)
        } else {
            synchronized(lock) {
                if (chainId in containerProcesses) {
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
                if (chainId in containerProcesses) {
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
        val process = createProcess(chainId).apply { state = ProcessState.STARTING }
        containerProcesses[chainId] = process
        logChain("startContainerChain", process)

        return try {
            // Creating working dir
            val (containerCwd, chainCwd) = containerInitializer.createContainerWorkingDir(process)

            // Creating nodeConfig for container node
            containerInitializer.createContainerNodeConfig(process, containerCwd)

            // Creating chain configs
            containerInitializer.createContainerChainConfigs(dataSource, process, chainCwd)

            // Creating a container if not exists
            val container = findChainContainer(process.containerName)
            process.containerId = if (container != null) {
                logger.info(m("startContainerChain: Container ${process.containerName} already exists: ${process.containerId}"))
                container.id()
            } else {
                logger.info(m("startContainerChain: Container ${process.containerName} is being created"))
                val config = ContainerConfigFactory.createConfig(containerCwd)
                val containerId = dockerClient.createContainer(config, process.containerName).id()
                // TODO: [POS-129]: Handle the case when containerId is null
                logger.info(m("startContainerChain: Container ${process.containerName} has been created: $containerId"))
                containerId
            }

            logger.info(m("startContainerChain: Container ${process.containerName} is starting up"))
            dockerClient.startContainer(process.containerId)
            process.state = ProcessState.RUNNING
            logger.info(m("startContainerChain: Container ${process.containerName} is running"))
            process.blockchainRid
        } catch (e: Exception) {
            // TODO: [POS-129]: Improve error handling/logging
            process.state = ProcessState.STOPPING
            containerProcesses.remove(chainId)
            null
        }
    }

    private fun stopContainerChain(chainId: Long) {
        val process = containerProcesses.remove(chainId)
                ?.apply { state = ProcessState.STOPPING }
        if (process == null) {
            logger.info(m("stopContainerChain: Can't find chain by chainId: $chainId"))
        } else {
            logChain("stopContainerChain", process)
            if (process.containerId != null) {
                logger.info(m("stopContainerChain: Container ${process.containerName} is stopping"))
                dockerClient.stopContainer(process.containerId, 10)
                logger.info(m("stopContainerChain: Container ${process.containerName} stopped"))
            } else {
                logger.error(m("stopContainerChain: Can't stop container ${process.containerName}, containerId is null"))
            }
        }
    }

    override fun shutdown() {
        startingOrRunningProcesses()
                .forEach { stopBlockchain(it) }
        super.shutdown()
    }

    private fun logChain(caller: String, process: ContainerBlockchainProcess) {
        val message = "$caller: " +
                "chainId: ${process.chainId}, " +
                "blockchainRid: ${process.blockchainRid}, " +
                "containerName: ${process.containerName}, " +
                "containerId: ${process.containerId}"
        logger.info(m(message))
    }

    // Just for tests
    private fun m(message: String) = "\t" + message

    private fun createProcess(chainId: Long): ContainerBlockchainProcess {
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        val processName = BlockchainProcessName(nodeConfig.pubKey, brid)
        return masterBlockchainInfra.makeMasterBlockchainProcess(
                processName, chainId, brid)
    }

    private fun findChainContainer(containerName: String): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.find { it.names()?.contains("/$containerName") ?: false } // Prefix /
    }

    override fun getLaunchedBlockchains(): Set<Long> {
        return super.getLaunchedBlockchains() + startingOrRunningProcesses()
    }

    private fun startingOrRunningProcesses(): Set<Long> {
        return containerProcesses.filterValues {
            it.state == ProcessState.STARTING || it.state == ProcessState.RUNNING
        }.keys
    }
}
