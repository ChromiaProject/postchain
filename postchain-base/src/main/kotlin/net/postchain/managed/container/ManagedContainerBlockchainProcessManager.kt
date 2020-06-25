package net.postchain.managed.container

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import net.postchain.base.BlockchainRid
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedBlockchainProcessManager
import java.nio.file.Path
import java.nio.file.Paths

class ManagedContainerBlockchainProcessManager(
        blockchainInfrastructure: BlockchainInfrastructure,
        nodeConfigProvider: NodeConfigurationProvider,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : ManagedBlockchainProcessManager(
        blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext
) {

    private val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
    private val runningChains = mutableMapOf<Long, ContainerChain>()
    private val lock = Any()

    override fun startBlockchain(chainId: Long): BlockchainRid? {
        return if (chainId == CHAIN0) {
            super.startBlockchain(chainId)
        } else {
            synchronized(lock) {
                if (runningChains.contains(chainId)) {
                    val state = runningChains[chainId]?.container?.state() ?: "<unknown-state>"
                    log("startBlockchain: container chain $chainId is $state")
                    runningChains[chainId]?.blockchainRid
                } else {
                    log("startBlockchain: container chain $chainId will start now")
                    runningChains[chainId] = buildContainerChain(chainId)
                    startContainerChain(chainId)
                }
            }
        }
    }

    override fun stopBlockchain(chainId: Long) {
        if (chainId == CHAIN0) {
            super.stopBlockchain(chainId)
        } else {
            synchronized(lock) {
                if (runningChains.contains(chainId)) {
                    val state = runningChains[chainId]?.container?.state() ?: "<unknown-state>"
                    log("stopBlockchain: container chain $chainId will stop now: $state")
                    stopContainerChain(chainId)
                } else {
                    log("stopBlockchain: container chain $chainId not running")
                }
            }
        }
    }

    override fun isBlockchainRunning(chainId: Long): Boolean {
        return if (chainId == 0L) {
            super.isBlockchainRunning(chainId)
        } else {
            log("Chain $chainId is running: ${runningChains.contains(chainId)}")
            runningChains.contains(chainId)
//            dockerClient.inspectContainer("")
        }
    }

    private fun startContainerChain(chainId: Long): BlockchainRid? {
        val chain = updateContainerChain(chainId)
        logChain("startContainerChain", chain)

        if (chain.container != null) {
//            startContainer(chain)
//            dockerClient.unpauseContainer(chain.containerId)
            dockerClient.startContainer(chain.containerId)
//            dockerClient.restartContainer(chain.containerId)
        } else {
            // Creating runtime-environment (rte) dir
            /**
             *  Windows/Docker: WSL doesn't work correctly with /mnt/.
             *  We should mount /mnt/d to /d and remove prefix '/mnt' everywhere.
             */
            val rteDir = nodeConfig.appConfig.configDir.removePrefix("/mnt")
            val containerRteDir = Paths.get(rteDir, "containers")
            val containerChainDir = containerRteDir.resolve("blockchains").resolve("$chainId")
            log("Container chain dir: $containerChainDir")
            val created = containerChainDir.toFile().mkdirs()
            log("Container chain dir created: $created")

            // Getting blockchainRid
            chain.blockchainRid = withReadConnection(storage, chainId) { ctx ->
                DatabaseAccess.of(ctx).getBlockchainRid(ctx)
            }
            log("BlockchainRid: ${chain.blockchainRid}")

            // Dumping all chain configs to chain dir
            val configs = try {
                dataSource.getConfigurations(chain.blockchainRid?.data ?: byteArrayOf())
            } catch (e: Exception) {
                log("Exception in dataSource.getConfigurations(): " + e.message)
                mapOf<Long, ByteArray>()
            }
            log("Configs to dump: ${configs.size}")
            configs.forEach { (height, config) ->
                val configPath = containerChainDir.resolve("$height.gtv")
                log("Config file dumped: $configPath")
                configPath.toFile().writeBytes(config)
            }

            // Creating and starting container
            chain.containerId = createContainer(containerRteDir, chain)
            startContainer(chain)
        }

        return chain.blockchainRid
    }

    private fun stopContainerChain(chainId: Long) {
        val chain = updateContainerChain(chainId)
        logChain("stopContainerChain", chain)

        if (chain.container != null) {
            log("stopContainerChain: container will stop: ${chain.containerName}")
            dockerClient.stopContainer(chain.containerId, 10)
//            dockerClient.pauseContainer(chain.containerId)
            runningChains.remove(chainId)
        } else {
            log("stopContainerChain: container not found: ${chain.containerName}")
            runningChains.remove(chainId)
        }
    }

    private fun logChain(caller: String, chain: ContainerChain) {
        log("$caller: " +
                "chainId: ${chain.chainId}, " +
                "blockchainRid: ${chain.blockchainRid}, " +
                "containerName: ${chain.containerName}, " +
                "containerId: ${chain.containerId}, " +
                "status: ${chain.container?.status()}, " +
                "state: ${chain.container?.state()}")
    }

    private fun log(message: String) {
        val prefix = "\t"
        logger.info { prefix + message }
    }

    private fun buildContainerChain(chainId: Long): ContainerChain = ContainerChain(nodeConfig.pubKey, chainId)

    private fun updateContainerChain(chainId: Long): ContainerChain {
        val chain = runningChains.computeIfAbsent(chainId) { buildContainerChain(chainId) }
        val containers = dockerClient.listContainers(ListContainersParam.allContainers())
        chain.container = containers.find { it.names()?.contains("/" + chain.containerName) ?: false } // Prefix /
        chain.containerId = chain.container?.id()

        val message = containers.joinToString(
                separator = "\n",
                prefix = "Containers:\n"
        ) { c -> c.id() + " => " + c.names()?.toTypedArray()?.contentToString() }
        log(message)

        return chain
    }

    private fun createContainer(containerRteDir: Path, chain: ContainerChain): String? {
        // -v /d/Home/Dev/ChromaWay/postchain2/postchain-distribution/src/main/postchain-subnode/docker/rte:/opt/chromaway/postchain-subnode/rte \
        log("Bind:: from: $containerRteDir to: /opt/chromaway/postchain-subnode/rte")
        val volume = HostConfig.Bind
                .from(containerRteDir.toString())
                .to("/opt/chromaway/postchain-subnode/rte")
                .build()

        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .build()

        val containerConfig = ContainerConfig.builder()
                .image("chromaway/postchain-subnode:3.2.1")
                .hostConfig(hostConfig)
                .build()

        val containerCreation = dockerClient.createContainer(containerConfig, chain.containerName)
        log("Container created: ${containerCreation.id()}")
        return containerCreation.id()
    }

    private fun startContainer(chain: ContainerChain) {
        dockerClient.startContainer(chain.containerId)
    }

    override fun getLaunchedBlockchains(): Set<Long> {
        return super.getLaunchedBlockchains() + runningChains.keys
    }
}