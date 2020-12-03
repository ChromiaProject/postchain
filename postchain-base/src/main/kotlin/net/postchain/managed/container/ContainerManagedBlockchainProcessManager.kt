package net.postchain.managed.container

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import net.postchain.base.BlockchainRid
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.ManagedBlockchainProcessManager
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class ContainerManagedBlockchainProcessManager(
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
                if (chainId in runningChains) {
                    log("startBlockchain: container chain $chainId is stopping")
                    stopContainerChain(chainId)
                    log("startBlockchain: container chain $chainId stopped")
                }

                log("startBlockchain: container chain $chainId is starting up")
                val brid = startContainerChain(chainId)
                log("startBlockchain: container chain $chainId is running")

                brid
            }
        }
    }

    override fun stopBlockchain(chainId: Long) {
        if (chainId == CHAIN0) {
            super.stopBlockchain(chainId)
        } else {
            synchronized(lock) {
                if (chainId in runningChains) {
                    log("stopBlockchain: container chain $chainId is stopping")
                    stopContainerChain(chainId)
                    log("stopBlockchain: container chain $chainId stopped")
                } else {
                    log("stopBlockchain: container chain $chainId stopped")
                }
            }
        }
    }

    private fun startContainerChain(chainId: Long): BlockchainRid? {
        val chain = buildContainerChain(chainId)
        logChain("startContainerChain", chain)

        // Creating working dir
        val (containerCwd, chainCwd) = createContainerWorkingDir(chain)

        // Dumping chain configs
        dumpChainConfigs(chain, chainCwd)

        // Creating a container
        val container = findChainContainer(chain.containerName)
        chain.containerId = if (container != null) {
            log("Container ${chain.containerName} already exists: ${chain.containerId}")
            container.id()
        } else {
            val containerId = ContainerFactory.createContainer(dockerClient, containerCwd, chain)
            // TODO: [POS-129]: Handle the case: containerId is null
            log("Container ${chain.containerName} has been created: ${chain.containerId}")
            containerId
        }

        dockerClient.startContainer(chain.containerId)
        runningChains[chainId] = chain

        return chain.blockchainRid
    }

    private fun stopContainerChain(chainId: Long) {
        val chain = runningChains[chainId]!!
        logChain("stopContainerChain", chain)

        if (chain.containerId != null) {
            log("stopContainerChain: container ${chain.containerName} is stopping")
            dockerClient.stopContainer(chain.containerId, 10)
        } else {
            log("stopContainerChain: can't stop container ${chain.containerName}, containerId is null")
        }

        runningChains.remove(chainId)
    }

    private fun logChain(caller: String, chain: ContainerChain) {
        log("$caller: " +
                "chainId: ${chain.chainId}, " +
                "blockchainRid: ${chain.blockchainRid}, " +
                "containerName: ${chain.containerName}, " +
                "containerId: ${chain.containerId}, "
//                +
//                "status: ${chain.container?.status()}, " +
//                "state: ${chain.container?.state()}"
        )
    }

    private fun log(message: String) {
        val prefix = "\t"
        logger.info { prefix + message }
    }

    private fun buildContainerChain(chainId: Long): ContainerChain {
        val brid = withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)
        }
        // TODO: [POS-129]: Add correct error handling (unreachable state)
        return ContainerChain(nodeConfig.pubKey, chainId, brid!!)
    }

    private fun findChainContainer(containerName: String): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.find { it.names()?.contains("/$containerName") ?: false } // Prefix /
    }

    private fun getChainConfigurations(blockchainRid: BlockchainRid): Map<Long, ByteArray> {
        return try {
            dataSource.getConfigurations(blockchainRid.data)
        } catch (e: Exception) {
            log("Exception in dataSource.getConfigurations(): " + e.message)
            mapOf()
        }
    }

    private fun createContainerWorkingDir(chain: ContainerChain): Pair<Path, Path> {
        // Creating current working dir (or runtime-environment (rte) dir)
        /**
         *  Windows/Docker: WSL doesn't work correctly with /mnt/.
         *  We should mount /mnt/d to /d and remove prefix '/mnt' everywhere.
         */
        val cwd = nodeConfig.appConfig.configDir.removePrefix("/mnt")
        val containerCwd = Paths.get(cwd, "containers", chain.blockchainRid.toHex().take(8)) // TODO: [POS-129]
        val containerChainDir = containerCwd.resolve("blockchains${File.separator}${chain.chainId}")
        if (containerChainDir.toFile().exists()) {
            log("Container chain dir exists: $containerChainDir")
        } else {
            val created = containerChainDir.toFile().mkdirs()
            log("Container chain dir ${if (created) "has" else "hasn't"} been created: $containerChainDir")
        }

        return containerCwd to containerChainDir
    }

    private fun dumpChainConfigs(chain: ContainerChain, chainDir: Path) {
        // Dumping all chain configs to chain dir
        // TODO: [POS-129]: Skip already dumped configs
        val configs = getChainConfigurations(chain.blockchainRid)
        log("Number of chain configs to dump: ${configs.size}")
        configs.forEach { (height, config) ->
            val configPath = chainDir.resolve("$height.gtv")
            configPath.toFile().writeBytes(config)
            log("Config file dumped: $configPath")
        }
    }

    override fun getLaunchedBlockchains(): Set<Long> {
        return super.getLaunchedBlockchains() + runningChains.keys
    }
}
