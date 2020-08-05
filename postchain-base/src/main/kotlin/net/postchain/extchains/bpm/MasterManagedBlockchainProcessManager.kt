package net.postchain.extchains.bpm

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.postchain.base.BlockchainRid
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.FileNodeConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.Infrastructures
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.devtools.PeerNameHelper
import net.postchain.extchains.infra.MasterBlockchainInfrastructure
import net.postchain.managed.ManagedBlockchainProcessManager
import org.apache.commons.configuration2.ConfigurationUtils
import java.nio.file.Path
import java.nio.file.Paths

class MasterManagedBlockchainProcessManager(
        private val masterBlockchainInfra: MasterBlockchainInfrastructure,
        nodeConfigProvider: NodeConfigurationProvider,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : ManagedBlockchainProcessManager(
        masterBlockchainInfra,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext
) {

    private val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
    private val runningChains = mutableMapOf<Long, ContainerChain>()
    private val slaveBlockchainProcesses = mutableMapOf<Long, ExternalBlockchainProcess>()
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
            runningChains.contains(chainId)
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
            val containerRteDir = Paths.get(rteDir, "containers", "$chainId")
            val chainConfigsDir = containerRteDir.resolve("blockchains").resolve("$chainId")
            log("Container chain configs dir: $chainConfigsDir")
            val created = chainConfigsDir.toFile().mkdirs()
            log("Container chain dir created: $created")

            // Creating node-config for container node
            createContainerNodeConfig(chainId, containerRteDir)
            log("Container sub-node properties file has been created")

            // Getting blockchainRid
            chain.blockchainRid = withReadConnection(storage, chainId) { ctx ->
                DatabaseAccess.of(ctx).getBlockchainRid(ctx)
            }
            log("BlockchainRid: ${chain.blockchainRid}")
            // TODO: Exception if blockchainRid is null

            // Dumping all chain configs to chain dir
            dumpConfigs(chain, chainConfigsDir)

            // Creating and starting container
            chain.containerId = createContainer(containerRteDir, chain)
            dockerClient.startContainer(chain.containerId)

            // Creating external blockchain process
            val processName = BlockchainProcessName(nodeConfig.pubKey, chain.blockchainRid!!)
            slaveBlockchainProcesses[chainId] = masterBlockchainInfra.makeSlaveBlockchainProcess(
                    chainId, chain.blockchainRid!!, processName)

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
            slaveBlockchainProcesses.remove(chainId) // TODO: [POS-129]: Redesign this
        } else {
            log("stopContainerChain: container not found: ${chain.containerName}")
            runningChains.remove(chainId)
        }
    }

    // TODO: [POS-129]: Implement it
    override fun shutdown() {
        super.shutdown()
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
        log("Bind from $containerRteDir to /opt/chromaway/postchain-subnode/rte")
        val volume = HostConfig.Bind
                .from(containerRteDir.toString())
                .to("/opt/chromaway/postchain-subnode/rte")
                .build()

        // container-port -> [host-port]
        val containerPort = nodeConfig.restApiPortDefault
        val hostPort = nodeConfig.restApiPort + 10 * chain.chainId.toInt() // TODO: [POS-129]: Implement port selection
        val hostConfig = HostConfig.builder()
                .appendBinds(volume)
                .portBindings(
                        mapOf(
                                "$containerPort/tcp" to listOf(PortBinding.of("", hostPort))
                        )
                )
                .build()

        val containerConfig = ContainerConfig.builder()
                .image("chromaway/postchain-subnode:3.2.1")
                .hostConfig(hostConfig)
                .build()

        val containerCreation = dockerClient.createContainer(containerConfig, chain.containerName)
        log("Container created: ${containerCreation.id()}")
        return containerCreation.id()
    }

    private fun createContainerNodeConfig(chainId: Long, containerRteDir: Path) {
        // Making a copy of configuration object
        val containerNodeConfigFile = containerRteDir.resolve("node-config.properties").toString()
        val subnodeConfig = ConfigurationUtils.cloneConfiguration(nodeConfig.appConfig.config)

        // Replacing/adding some properties
        subnodeConfig.setProperty("configuration.provider.node", "file")
        subnodeConfig.setProperty("infrastructure", Infrastructures.EbftSlave.key)
        val scheme = "${nodeConfig.appConfig.databaseSchema}_${PeerNameHelper.peerName(nodeConfig.pubKey)}_$chainId"
        subnodeConfig.setProperty("database.schema", scheme)

        subnodeConfig.setProperty("api.port", nodeConfig.restApiPortDefault)

        subnodeConfig.setProperty("externalChains.masterHost", nodeConfig.masterHost)
        subnodeConfig.setProperty("externalChains.masterPort", nodeConfig.masterPort)

        // Adding peerInfos property as array/list
        val peerInfos = nodeConfig.peerInfoMap.values.map { FileNodeConfigurationProvider.packPeerInfo(it) }
        subnodeConfig.setProperty("peerinfos", peerInfos)

        // Saving .properties file
        AppConfig.toPropertiesFile(containerNodeConfigFile, subnodeConfig)
    }

    private fun dumpConfigs(chain: ContainerChain, chainConfigsDir: Path) {
        // Retrieving configs from data-source/db
        val configs = try {
            dataSource.getConfigurations(chain.blockchainRid?.data ?: byteArrayOf())
        } catch (e: Exception) {
            log("Exception in dataSource.getConfigurations(): " + e.message)
            mapOf<Long, ByteArray>()
        }

        // Dumping configs to chain-configs dir
        log("Configs to dump: ${configs.size}")
        configs.forEach { (height, config) ->
            val configPath = chainConfigsDir.resolve("$height.gtv")
            log("Config file dumped: $configPath")
            configPath.toFile().writeBytes(config)
        }
    }

    override fun getLaunchedBlockchains(): Set<Long> {
        return super.getLaunchedBlockchains() + runningChains.keys
    }
}