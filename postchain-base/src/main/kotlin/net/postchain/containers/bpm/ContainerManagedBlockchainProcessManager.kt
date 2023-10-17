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
    private val chains: MutableMap<Long, Chain> = mutableMapOf() // chainId -> Chain
    private val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
    private val dockerClient: DockerClient = ContainerEnvironment.dockerClient
    private val postchainContainers = Collections.synchronizedMap(LinkedHashMap<ContainerName, PostchainContainer>()) // { ContainerName -> PsContainer }
    private val fileSystem = FileSystem.create(containerNodeConfig)
    private val containerHealthcheckHandler = ContainerHealthcheckHandler(dockerClient, fileSystem, ::containers, ::removeBlockchainProcess)
    private val containerJobHandler = ContainerJobHandler(appConfig, nodeDiagnosticContext, dockerClient, fileSystem,
            ::directoryDataSource, ::containers, ::terminateBlockchainProcess, ::createBlockchainProcess)
    private val containerJobManager = DefaultContainerJobManager(containerNodeConfig, containerJobHandler, containerHealthcheckHandler)
    private val runningInContainer = System.getenv("POSTCHAIN_RUNNING_IN_CONTAINER").toBoolean()

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
                    // Pruning removed blockchains if exist
                    pruneRemovedBlockchains()
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
        val subnodeLaunched: Map<Long, ContainerBlockchainProcess> = getStartingOrRunningContainerBlockchains()

        // Chain0
        if (reloadChain0) {
            logger.debug("ContainerJob -- Restart chain0")
            startBlockchainAsync(CHAIN0, null)
        }

        // Stopping launched blockchains
        masterLaunched.keys.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug { "ContainerJob -- Stop system chain: $it" }
            stopBlockchainAsync(it, null)
        }
        subnodeLaunched.keys.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug { "ContainerJob -- Stop subnode chain: ${getChain(it)}" }
            containerJobManager.stopChain(getChain(it))
        }

        // Launching new and updated state blockchains except blockchain 0
        toLaunch.filter { it.chainId != CHAIN0 }.forEach {
            if (it.system) {
                val process = masterLaunched[it.chainId]
                if (process == null) {
                    logger.debug { "ContainerJob -- Start system chain: ${it.chainId}" }
                    startBlockchainAsync(it.chainId, null)
                } else if (process.getBlockchainState() != it.state) {
                    logger.debug { "ContainerJob -- Restart system chain due to state change: ${it.chainId}" }
                    startBlockchainAsync(it.chainId, null)
                }
            } else {
                val process = subnodeLaunched[it.chainId]
                if (process == null) {
                    logger.debug { "ContainerJob -- Start subnode chain: ${getChain(it.chainId)}" }
                    containerJobManager.startChain(getChain(it.chainId))
                } else if (process.blockchainState != it.state) {
                    logger.debug { "ContainerJob -- Restart subnode chain due to state change: ${getChain(it.chainId)}" }
                    containerJobManager.startChain(getChain(it.chainId))
                }
            }
        }
    }

    override fun shutdown() {
        getStartingOrRunningContainerBlockchains()
                .forEach { stopBlockchain(it.key, bTrace = null) }
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

    /**
     * Called when subnode has committed a block
     */
    override fun onAfterCommitInSubnode(blockchainRid: BlockchainRid, blockHeight: Long) {
        extensions.filterIsInstance(ContainerBlockchainProcessManagerExtension::class.java).forEach {
            it.afterCommitInSubnode(blockchainRid, blockHeight)
        }
    }
}
