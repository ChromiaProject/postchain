package net.postchain.containers.bpm

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.ContainerState.RUNNING
import net.postchain.containers.bpm.ContainerState.STARTING
import net.postchain.containers.bpm.chain0.ContainerChain0BlockchainConfigurationFactory
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.containers.bpm.docker.DockerTools.containerName
import net.postchain.containers.bpm.docker.DockerTools.shortContainerId
import net.postchain.containers.bpm.job.ContainerHealthcheckHandler
import net.postchain.containers.bpm.job.ContainerJobHandler
import net.postchain.containers.bpm.job.DefaultContainerJobManager
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactorySupplier
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.core.RemoteBlockchainProcessConnectable
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.logging.CONTAINER_NAME_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.LocalBlockchainInfo
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import org.mandas.docker.client.DockerClient

const val POSTCHAIN_MASTER_PUBKEY = "postchain-master-pubkey"

open class ContainerManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        private val masterBlockchainInfra: MasterBlockchainInfra,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : ManagedBlockchainProcessManager(
        postchainContext,
        masterBlockchainInfra,
        blockchainConfigProvider,
        bpmExtensions
), AfterSubnodeCommitListener {

    companion object : KLogging()

    private val directoryDataSource: DirectoryDataSource by lazy { dataSource as DirectoryDataSource }
    private val chains: MutableMap<Long, Chain> = mutableMapOf() // chainId -> Chain
    private val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
    private val dockerClient: DockerClient = DockerClientFactory.create()
    private val postchainContainers = mutableMapOf<ContainerName, PostchainContainer>() // { ContainerName -> PsContainer }
    private val containerHealthcheckHandler = ContainerHealthcheckHandler(appConfig, nodeName(), dockerClient,
            ::containers, ::removeBlockchainProcess)
    private val containerJobHandler = ContainerJobHandler(appConfig, nodeDiagnosticContext, nodeName(), dockerClient,
            ::directoryDataSource, ::containers, ::terminateBlockchainProcess, ::createBlockchainProcess)
    private val containerJobManager = DefaultContainerJobManager(containerNodeConfig, containerJobHandler, containerHealthcheckHandler)
    private val runningInContainer = System.getenv("POSTCHAIN_RUNNING_IN_CONTAINER").toBoolean()

    init {
        logger.info(if (runningInContainer) "Running in container" else "Running as native process")
        try {
            dockerClient.ping()
        } catch (e: Exception) {
            logger.error("Unable to access Docker daemon: $e")
        }
        try {
            removeContainersIfExist()
        } catch (e: Exception) {
            logger.error("Unable to list/remove containers: $e")
        }
        Runtime.getRuntime().addShutdownHook(
                Thread {
                    withLoggingContext(NODE_PUBKEY_TAG to appConfig.pubKey) {
                        logger.info("Shutting down master node")
                        shutdown()
                        logger.info("Stopping subnode containers...")
                        for ((name, psContainer) in postchainContainers) {
                            withLoggingContext(CONTAINER_NAME_TAG to psContainer.containerName.name) {
                                logger.info("Stopping subnode $name...")
                                psContainer.stop()
                                dockerClient.stopContainer(psContainer.containerId, 10)
                            }
                        }
                        logger.info("Stopping subnode containers done")
                    }
                }
        )
    }

    private fun containers(): MutableMap<ContainerName, PostchainContainer> = postchainContainers

    override fun initManagedEnvironment(dataSource: ManagedNodeDataSource) {
        masterBlockchainInfra.masterConnectionManager.dataSource = dataSource
        super.initManagedEnvironment(dataSource)
    }

    override fun getBlockchainConfigurationFactory(chainId: Long): BlockchainConfigurationFactorySupplier =
            BlockchainConfigurationFactorySupplier { factoryName: String ->
                val factory = try {
                    newInstanceOf<GTXBlockchainConfigurationFactory>(factoryName)
                } catch (e: Exception) {
                    throw UserMistake("[${nodeName()}]: Can't start blockchain chainId: $chainId " +
                            "due to configuration is wrong. Check /configurationfactory value: $factoryName." +
                            "Use ${GTXBlockchainConfigurationFactory::class.qualifiedName} (or subclass) for chain0.", e)
                }
                if (chainId == CHAIN0) {
                    ContainerChain0BlockchainConfigurationFactory(appConfig, factory, containerNodeConfig, blockBuilderStorage)
                } else {
                    DappBlockchainConfigurationFactory(factory, dataSource)
                }
            }

    override fun buildAfterCommitHandler(chainId: Long, blockchainConfig: BlockchainConfiguration): AfterCommitHandler {
        fun chain0AfterCommitHandler(blockTrace: BlockTrace?, blockHeight: Long, blockTimestamp: Long): Boolean {
            return try {
                rTrace("Before", chainId, blockTrace)

                // If chain is already being stopped/restarted by another thread we will not get the lock and may return
                if (!tryAcquireChainLock(chainId)) return false

                invokeAfterCommitHooks(chainId, blockHeight)

                // Preloading blockchain configuration
                preloadChain0Configuration()

                rTrace("Sync", chainId, blockTrace)
                val res = containerJobManager.withLock {
                    // Reload/start/stops blockchains
                    rTrace("about to restart chain0", chainId, blockTrace)
                    // Checking out for chain0 configuration changes
                    val reloadChain0 = isConfigurationChanged(CHAIN0)
                    stopStartBlockchains(reloadChain0)
                    reloadChain0
                }

                rTrace("After", chainId, blockTrace)
                res
            } catch (e: Exception) {
                logger.error(e) { "Exception in RestartHandler: $e" }
                startBlockchainAsync(chainId, blockTrace)
                true // let's hope restarting a blockchain fixes the problem
            } finally {
                releaseChainLock(chainId)
            }
        }

        return if (chainId == CHAIN0) {
            ::chain0AfterCommitHandler
        } else {
            super.buildAfterCommitHandler(chainId, blockchainConfig)
        }
    }

    private fun stopStartBlockchains(reloadChain0: Boolean) {
        val toLaunch: Set<LocalBlockchainInfo> = retrieveBlockchainsToLaunch()
        val chainIdsToLaunch: Set<Long> = toLaunch.map { it.chainId }.toSet()
        val masterLaunched: Map<Long, BlockchainProcess> = getLaunchedBlockchains()
        val subnodeLaunched: Map<Long, ContainerBlockchainProcess> = getStartingOrRunningContainerBlockchains()

        // Chain0
        if (reloadChain0) {
            logger.debug("[${nodeName()}]: ContainerJob -- Restart chain0")
            startBlockchainAsync(CHAIN0, null)
        }

        // Stopping launched blockchains
        masterLaunched.keys.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug { "[${nodeName()}]: ContainerJob -- Stop system chain: $it" }
            stopBlockchainAsync(it, null)
        }
        subnodeLaunched.keys.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug { "[${nodeName()}]: ContainerJob -- Stop subnode chain: ${getChain(it)}" }
            containerJobManager.stopChain(getChain(it))
        }

        // Launching new and updated state blockchains except blockchain 0
        toLaunch.filter { it.chainId != CHAIN0 }.forEach {
            if (it.system) {
                val process = masterLaunched[it.chainId]
                if (process == null) {
                    logger.debug { "[${nodeName()}]: ContainerJob -- Start system chain: ${it.chainId}" }
                    startBlockchainAsync(it.chainId, null)
                } else if (process.getBlockchainState() != it.state) {
                    logger.debug { "[${nodeName()}]: ContainerJob -- Restart system chain due to state change: ${it.chainId}" }
                    startBlockchainAsync(it.chainId, null)
                }
            } else {
                val process = subnodeLaunched[it.chainId]
                if (process == null) {
                    logger.debug { "[${nodeName()}]: ContainerJob -- Start subnode chain: ${getChain(it.chainId)}" }
                    containerJobManager.startChain(getChain(it.chainId))
                } else if (process.blockchainState != it.state) {
                    logger.debug { "[${nodeName()}]: ContainerJob -- Restart subnode chain due to state change: ${getChain(it.chainId)}" }
                    containerJobManager.startChain(getChain(it.chainId))
                }
            }
        }
    }

    override fun shutdown() {
        getStartingOrRunningContainerBlockchains()
                .forEach { stopBlockchain(it.key, bTrace = null) }
        containerJobManager.shutdown()
        super.shutdown()
    }

    private fun createBlockchainProcess(chain: Chain, psContainer: PostchainContainer): ContainerBlockchainProcess? {
        val blockchainState = directoryDataSource.getBlockchainState(chain.brid)
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                BlockchainProcessName(appConfig.pubKey, chain.brid),
                chain.chainId,
                chain.brid,
                directoryDataSource,
                psContainer,
                blockchainState
        )

        nodeDiagnosticContext.blockchainData(chain.brid).putAll(mapOf(
                DiagnosticProperty.BLOCKCHAIN_LAST_HEIGHT withLazyValue { psContainer.getBlockchainLastHeight(process.chainId) },
                DiagnosticProperty.CONTAINER_NAME withValue psContainer.containerName.toString(),
                DiagnosticProperty.CONTAINER_ID withValue (psContainer.shortContainerId() ?: ""),
        ))

        val started = psContainer.startProcess(process)
        if (started) {
            chainIdToBrid[chain.chainId] = chain.brid
            bridToChainId[chain.brid] = chain.chainId
            extensions.filterIsInstance<RemoteBlockchainProcessConnectable>()
                    .forEach { it.connectRemoteProcess(process) }
        }

        return process.takeIf { started }
    }

    private fun removeBlockchainProcess(chainId: Long, psContainer: PostchainContainer): ContainerBlockchainProcess? =
            psContainer.removeProcess(chainId)
                    ?.also { cleanUpBlockchainProcess(chainId, it) }

    private fun terminateBlockchainProcess(chainId: Long, psContainer: PostchainContainer): ContainerBlockchainProcess? =
            psContainer.terminateProcess(chainId)
                    ?.also { cleanUpBlockchainProcess(chainId, it) }

    private fun cleanUpBlockchainProcess(chainId: Long, process: ContainerBlockchainProcess) {
        extensions.filterIsInstance<RemoteBlockchainProcessConnectable>()
                .forEach { it.disconnectRemoteProcess(process) }
        masterBlockchainInfra.exitMasterBlockchainProcess(process)
        val blockchainRid = chainIdToBrid.remove(chainId)
        nodeDiagnosticContext.removeBlockchainData(blockchainRid)
        bridToChainId.remove(blockchainRid)
        chains.remove(chainId)
        process.shutdown()
    }

    private fun getStartingOrRunningContainerBlockchains(): Map<Long, ContainerBlockchainProcess> {
        return postchainContainers.values
                .filter { it.state == STARTING || it.state == RUNNING }
                .flatMap { it.getAllProcesses().toList() }
                .toMap()
    }

    private fun getChain(chainId: Long): Chain {
        return chains.computeIfAbsent(chainId) {
            val brid = getBridByChainId(chainId)
            val container = directoryDataSource.getContainerForBlockchain(brid)
            val containerIid = getContainerIid(container)
            val containerName = ContainerName.create(appConfig, container, containerIid)
            Chain(containerName, chainId, brid)
        }
    }

    private fun getBridByChainId(chainId: Long): BlockchainRid = withReadConnection(blockBuilderStorage, chainId) { ctx ->
        DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
    }

    private fun getContainerIid(name: String): Int = blockBuilderStorage.withWriteConnection { ctx ->
        DatabaseAccess.of(ctx).getContainerIid(ctx, name) ?: DatabaseAccess.of(ctx).createContainer(ctx, name)
    }

    private fun removeContainersIfExist() {
        val toStop = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).filter {
            (it.labels() ?: emptyMap())[POSTCHAIN_MASTER_PUBKEY] == containerNodeConfig.masterPubkey
        }

        if (toStop.isNotEmpty()) {
            logger.warn {
                "Containers found to be removed (${toStop.size}): ${toStop.joinToString(transform = ::containerName)}"
            }

            toStop.forEach {
                withLoggingContext(CONTAINER_NAME_TAG to containerName(it).drop(1)) {
                    try {
                        dockerClient.stopContainer(it.id(), 20)
                        logger.info { "Container has been stopped: ${containerName(it)} / ${shortContainerId(it.id())}" }
                    } catch (e: Exception) {
                        logger.error("Can't stop container: " + it.id(), e)
                    }

                    try {
                        dockerClient.removeContainer(it.id(), DockerClient.RemoveContainerParam.forceKill())
                        logger.info { "Container has been removed: ${containerName(it)} / ${shortContainerId(it.id())}" }
                    } catch (e: Exception) {
                        logger.error("Can't remove container: " + it.id(), e)
                    }
                }
            }
        }
    }

    /**
     * Called when subnode has committed a block
     */
    override fun onAfterCommitInSubnode(blockchainRid: BlockchainRid, blockRid: BlockRid, blockHeader: ByteArray, witnessData: ByteArray) {
        extensions.filterIsInstance(ContainerBlockchainProcessManagerExtension::class.java).forEach {
            it.afterCommitInSubnode(blockchainRid, blockRid, blockHeader = blockHeader, witnessData = witnessData)
        }
    }
}
