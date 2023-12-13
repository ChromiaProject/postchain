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
import net.postchain.common.types.WrappedByteArray
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.ContainerState.RUNNING
import net.postchain.containers.bpm.ContainerState.STARTING
import net.postchain.containers.bpm.chain0.ContainerChain0BlockchainConfigurationFactory
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.bpm.job.ContainerHealthcheckHandler
import net.postchain.containers.bpm.job.ContainerJobHandler
import net.postchain.containers.bpm.job.DefaultContainerJobManager
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactorySupplier
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.core.RemoteBlockchainProcessConnectable
import net.postchain.core.block.BlockTrace
import net.postchain.debug.DiagnosticProperty
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.logging.CONTAINER_NAME_TAG
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.LocalBlockchainInfo
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import net.postchain.metrics.ContainerMetrics
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import org.mandas.docker.client.DockerClient
import java.util.Collections

const val POSTCHAIN_MASTER_PUBKEY = "postchain-master-pubkey"

class ContainerManagedBlockchainProcessManager(
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
    private val chains: MutableMap<Pair<Long, ContainerName>, Chain> = mutableMapOf() // (chainId, containerName) -> Chain
    private val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
    private val dockerClient: DockerClient = ContainerEnvironment.dockerClient
    private val postchainContainers = Collections.synchronizedMap(LinkedHashMap<ContainerName, PostchainContainer>()) // { ContainerName -> PsContainer }
    private val fileSystem = FileSystem.create(containerNodeConfig)
    private val containerHealthcheckHandler = ContainerHealthcheckHandler(dockerClient, fileSystem, ::containers, ::removeBlockchainProcess)
    private val containerJobHandler = ContainerJobHandler(appConfig, nodeDiagnosticContext, dockerClient, fileSystem,
            ::directoryDataSource, ::containers, ::terminateBlockchainProcess, ::createBlockchainProcess)
    private val containerJobManager = DefaultContainerJobManager(
            containerNodeConfig, containerJobHandler, containerHealthcheckHandler, ::housekeepingHandler)
    private val runningInContainer = System.getenv("POSTCHAIN_RUNNING_IN_CONTAINER").toBoolean()
    private val blockchainReplicators: MutableMap<Long, BlockchainReplicator> = mutableMapOf()
    private val completedReplicationDetails: MutableMap<WrappedByteArray, Pair<Chain, Chain>> = mutableMapOf()

    private val metrics = ContainerMetrics(this)

    init {
        logger.info(if (runningInContainer) "Running in container" else "Running as native process")
        Runtime.getRuntime().addShutdownHook(
                Thread {
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
        )
    }

    fun numberOfSubnodes(): Int = containers().size

    fun numberOfContainers(): Int = directoryDataSource.getContainersToRun()?.size ?: 0

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
                    throw UserMistake("Can't start blockchain chainId: $chainId " +
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
        @Suppress("UNUSED_PARAMETER")
        fun chain0AfterCommitHandler(blockTrace: BlockTrace?, blockHeight: Long, blockTimestamp: Long): Boolean {
            return try {
                rTrace("Before", blockTrace)

                // If chain is already being stopped/restarted by another thread we will not get the lock and may return
                if (!tryAcquireChainLock(chainId)) return false

                invokeAfterCommitHooks(chainId, blockHeight)

                rTrace("Sync", blockTrace)
                val res = containerJobManager.withLock {
                    // Reload/start/stops blockchains
                    rTrace("about to restart chain0", blockTrace)
                    // Checking out for chain0 configuration changes
                    val reloadChain0 = isConfigurationChanged(CHAIN0)
                    stopStartBlockchains(reloadChain0)
                    reloadChain0
                }

                rTrace("After", blockTrace)
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
        val subnodeLaunched = getStartingOrRunningContainerBlockchains()

        // Chain0
        if (reloadChain0) {
            logger.debug { "ContainerJob -- Restart chain0" }
            startBlockchainAsync(CHAIN0, null)
        }

        // Stopping launched blockchains
        masterLaunched.keys.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug { "ContainerJob -- Stop system chain: $it" }
            stopBlockchainAsync(it, null)
        }
        subnodeLaunched.filterKeys { it !in chainIdsToLaunch }.values.forEach { containerProcesses ->
            containerProcesses.forEach {
                chains[it.second.chainId to it.first.containerName]?.also { chain ->
                    logger.debug { "ContainerJob -- Stop subnode chain: $chain" }
                    containerJobManager.stopChain(chain)
                }
            }
        }

        // Launching new and updated state blockchains except chain0
        toLaunch.filter { it.chainId != CHAIN0 }.forEach { bcInfo ->
            if (bcInfo.system) {
                val process = masterLaunched[bcInfo.chainId]
                if (process == null) {
                    logger.debug { "ContainerJob -- Start system chain: ${bcInfo.chainId}" }
                    startBlockchainAsync(bcInfo.chainId, null)
                } else if (process.getBlockchainState() != bcInfo.state) {
                    logger.debug { "ContainerJob -- Restart system chain due to state change: ${bcInfo.chainId}" }
                    startBlockchainAsync(bcInfo.chainId, null)
                } else {
                    logger.debug { "ContainerJob -- System chain already launched, nothing to do: ${bcInfo.chainId}" }
                }
            } else {
                val chains = getOrCreateContainerChains(bcInfo.chainId) // Support max 2 containers for now
                logger.debug { "Chains: ${chains.toTypedArray().contentToString()}" }

                if (chains.size == 1) {
                    startSubnodeChains(bcInfo, chains, subnodeLaunched[chains.first().chainId])
                } else if (chains.size > 1) {
                    val chain = chains.first()
                    val info = directoryDataSource.getUnarchivingBlockchainNodeInfo(chain.brid)
                    if (info != null) {
                        val srcChain = chains.firstOrNull { it.containerName.directoryContainer == info.sourceContainer }
                        val dstChain = chains.firstOrNull { it.containerName.directoryContainer == info.destinationContainer }
                        if (srcChain != null && dstChain != null) {
                            // If replication is completed, start only dst chain, otherwise start both chain and replication
                            if (completedReplicationDetails[info.rid]?.first == srcChain) {
                                startSubnodeChains(bcInfo, listOf(dstChain), subnodeLaunched[chains.first().chainId])
                            } else {
                                dstChain.restApiEnabled = false
                                startSubnodeChains(bcInfo, chains, subnodeLaunched[chains.first().chainId])
                                blockchainReplicators.getOrPut(chain.chainId) {
                                    BlockchainReplicator(info.rid, srcChain, dstChain, info.upToHeight, directoryDataSource, ::findPostchainContainer).also {
                                        logger.debug { "BlockchainReplicator started: rid: ${info.rid}, srcChain: $srcChain, dstChain: $dstChain" }
                                    }
                                }
                            }
                        } else {
                            startSubnodeChains(bcInfo, chains, subnodeLaunched[chains.first().chainId])
                        }
                    } else {
                        startSubnodeChains(bcInfo, chains, subnodeLaunched[chains.first().chainId])
                    }
                }
            }
        }

        blockchainReplicators.values.forEach { replicator ->
            if (replicator.isDone()) {
                containerJobManager.stopChain(replicator.srcChain)
                // Once dst chain is stopped, it will be restarted by the managed system
                containerJobManager.stopChain(replicator.dstChain)
                completedReplicationDetails[replicator.rid] = replicator.srcChain to replicator.dstChain
            } else if (replicator.upToHeight == -1L) {
                val info = directoryDataSource.getUnarchivingBlockchainNodeInfo(replicator.srcChain.brid)
                if (info != null && info.upToHeight != -1L) {
                    replicator.upToHeight = info.upToHeight
                }
            }
        }
        blockchainReplicators.entries.removeIf { it.value.isDone() }
    }

    private fun startSubnodeChains(
            bcInfo: LocalBlockchainInfo,
            chains: List<Chain>,
            launchedContainerProcesses: List<Pair<PostchainContainer, ContainerBlockchainProcess>>?
    ) {
        chains.forEach { chain ->
            logger.debug { "chain to be processed: $chain" }
            val process = launchedContainerProcesses
                    ?.firstOrNull { it.first.containerName == chain.containerName }?.second
            if (process == null) {
                logger.debug { "ContainerJob -- Start subnode chain: $chain" }
                containerJobManager.startChain(chain)
            } else if (process.blockchainState != bcInfo.state) {
                logger.debug { "ContainerJob -- Restart subnode chain due to state change: $chain" }
                containerJobManager.startChain(chain)
            } else {
                logger.debug { "ContainerJob -- Already launched, nothing to do: ${bcInfo.chainId}" }
            }
        }
    }

    private fun findPostchainContainer(containerName: ContainerName): PostchainContainer? =
            postchainContainers.values.firstOrNull { it.containerName == containerName }

    override fun shutdown() {
        getStartingOrRunningContainerBlockchains().forEach { stopBlockchain(it.key, null) }
        containerJobManager.shutdown()
        metrics.close()
        super.shutdown()
    }

    private fun createBlockchainProcess(chain: Chain, psContainer: PostchainContainer): ContainerBlockchainProcess? {
        val blockchainState = directoryDataSource.getBlockchainState(chain.brid)
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                chain.chainId,
                chain.brid,
                directoryDataSource,
                psContainer,
                blockchainState,
                chain.restApiEnabled
        )

        nodeDiagnosticContext.blockchainData(chain.brid).putAll(mapOf(
                DiagnosticProperty.BLOCKCHAIN_LAST_HEIGHT withLazyValue { psContainer.getBlockchainLastBlockHeight(process.chainId) },
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
                    ?.also { cleanUpBlockchainProcess(chainId, psContainer, it) }

    private fun terminateBlockchainProcess(chainId: Long, psContainer: PostchainContainer): ContainerBlockchainProcess? =
            psContainer.terminateProcess(chainId)
                    ?.also { cleanUpBlockchainProcess(chainId, psContainer, it) }

    private fun housekeepingHandler() {
        // Stopping idle containers
        postchainContainers.filterValues { it.isIdle() }.keys
                .forEach { containerName ->
                    logger.info { "Container is idle and will be stopped: $containerName" }
                    postchainContainers.remove(containerName)?.also { psContainer ->
                        psContainer.stop()
                        dockerClient.stopContainer(psContainer.containerId, 10)
                        logger.debug { "Docker container stopped: $containerName" }
                    }
                    logger.info { "Container stopped: $containerName" }
                }
    }

    private fun cleanUpBlockchainProcess(chainId: Long, psContainer: PostchainContainer, process: ContainerBlockchainProcess) {
        extensions.filterIsInstance<RemoteBlockchainProcessConnectable>()
                .forEach { it.disconnectRemoteProcess(process) }
        masterBlockchainInfra.exitMasterBlockchainProcess(process)
        val blockchainRid = chainIdToBrid.remove(chainId)
        nodeDiagnosticContext.removeBlockchainData(blockchainRid)
        bridToChainId.remove(blockchainRid)
        chains.remove(chainId to psContainer.containerName)
        process.shutdown()
    }

    private fun getStartingOrRunningContainerBlockchains(): Map<Long, List<Pair<PostchainContainer, ContainerBlockchainProcess>>> {
        return postchainContainers.values
                .filter { it.state == STARTING || it.state == RUNNING }
                .flatMap { cont -> cont.getAllProcesses().map { (chain, proc) -> Triple(chain, cont, proc) } }
                .groupBy({ it.first }, { it.second to it.third })
    }

    private fun getOrCreateContainerChains(chainId: Long): List<Chain> {
        val brid = getBridByChainId(chainId)
        return directoryDataSource.getBlockchainContainersForNode(brid).take(2) // Support max 2 containers for now
                .map { container ->
                    val containerName = ContainerName.create(appConfig, container, getContainerIid(container))
                    chains.computeIfAbsent(chainId to containerName) {
                        Chain(chainId, brid, containerName)
                    }
                }
    }

    private fun getBridByChainId(chainId: Long): BlockchainRid = withReadConnection(blockBuilderStorage, chainId) { ctx ->
        DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
    }

    private fun getContainerIid(name: String): Int = blockBuilderStorage.withWriteConnection { ctx ->
        DatabaseAccess.of(ctx).getContainerIid(ctx, name) ?: DatabaseAccess.of(ctx).createContainer(ctx, name)
    }

    /**
     * Called when subnode has committed a block
     */
    override fun onAfterCommitInSubnode(blockchainRid: BlockchainRid, blockHeight: Long) {
        extensions.filterIsInstance(ContainerBlockchainProcessManagerExtension::class.java).forEach {
            it.afterCommitInSubnode(blockchainRid, blockHeight)
        }
    }
}
