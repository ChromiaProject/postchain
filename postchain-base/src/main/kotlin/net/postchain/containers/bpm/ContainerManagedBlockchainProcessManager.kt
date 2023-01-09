package net.postchain.containers.bpm

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.ContainerState.RUNNING
import net.postchain.containers.bpm.ContainerState.STARTING
import net.postchain.containers.bpm.chain0.ContainerChain0BlockchainConfigurationFactory
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.containers.bpm.docker.DockerTools.containerName
import net.postchain.containers.bpm.docker.DockerTools.findHostPorts
import net.postchain.containers.bpm.docker.DockerTools.hasName
import net.postchain.containers.bpm.docker.DockerTools.shortContainerId
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.bpm.job.ContainerJob
import net.postchain.containers.bpm.job.DefaultContainerJobManager
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockRid
import net.postchain.core.BlockchainConfigurationFactorySupplier
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.core.RemoteBlockchainProcessConnectable
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.LocalBlockchainInfo
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import net.postchain.network.mastersub.master.AfterSubnodeCommitListener
import java.nio.file.Path

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
    private val fs = FileSystem.create(containerNodeConfig)
    private val dockerClient: DockerClient = DockerClientFactory.create()
    private val postchainContainers = mutableMapOf<ContainerName, PostchainContainer>() // { ContainerName -> PsContainer }
    private val containerJobManager = DefaultContainerJobManager(::containerJobHandler, ::containerHealthcheckJobHandler)

    init {
        removeContainersIfExist()
        Runtime.getRuntime().addShutdownHook(
                Thread {
                    logger.info("Shutting down master node - stopping subnode containers...")
                    for ((name, psContainer) in postchainContainers) {
                        logger.info("Stopping subnode $name...")
                        psContainer.stop()
                        dockerClient.stopContainer(psContainer.containerId, 10)
                    }
                    logger.info("Stopping subnode containers done")
                }
        )
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
                    ContainerChain0BlockchainConfigurationFactory(appConfig, factory, containerNodeConfig)
                } else {
                    DappBlockchainConfigurationFactory(factory, dataSource)
                }
            }

    override fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler = if (chainId == CHAIN0) {
        { blockTrace: BlockTrace?, blockHeight: Long, _: Long ->
            try {
                rTrace("Before", chainId, blockTrace)
                rTrace("Before", chainId, blockTrace)
                for (e in extensions) e.afterCommit(blockchainProcesses[chainId]!!, blockHeight)

                // Preloading blockchain configuration
                preloadChain0Configuration()

                // Checking out the peer list changes
                val peerListVersion = dataSource.getPeerListVersion()
                val doReload = (this.peerListVersion != peerListVersion)
                this.peerListVersion = peerListVersion

                rTrace("Sync", chainId, blockTrace)
                val res = containerJobManager.withLock {
                    // Reload/start/stops blockchains
                    val res2 = if (doReload) {
                        rInfo("peer list changed, reloading of blockchains is required", chainId, blockTrace)
                        reloadAllBlockchains()
                        true
                    } else {
                        rTrace("about to restart chain0", chainId, blockTrace)
                        // Checking out for chain0 configuration changes
                        val reloadChain0 = isConfigurationChanged(CHAIN0)
                        stopStartBlockchains(reloadChain0)
                        reloadChain0
                    }

                    // Docker containers healthcheck
                    scheduleDockerContainersHealthcheck()
                    res2
                }

                rTrace("After", chainId, blockTrace)
                res
            } catch (e: Exception) {
                logger.error(e) { "Exception in RestartHandler: $e" }
                startBlockchainAsync(chainId, blockTrace)
                true // let's hope restarting a blockchain fixes the problem
            }
        }
    } else {
        super.buildAfterCommitHandler(chainId)
    }

    /**
     * Restart all chains. Begin with chain zero.
     */
    private fun reloadAllBlockchains() {
        startBlockchainAsync(CHAIN0, null)

        getLaunchedBlockchains().filterNot { it == CHAIN0 }.forEach {
            logger.debug("[${nodeName()}]: ContainerJob -- restart system chain: $it")
            startBlockchainAsync(it, null)
        }

        postchainContainers.values.forEach { cont ->
            cont.getAllChains().forEach {
                logger.debug("[${nodeName()}]: ContainerJob -- restart subnode chain: ${getChain(it)}")
                containerJobManager.restartChain(getChain(it))
            }
        }
    }

    private fun stopStartBlockchains(reloadChain0: Boolean) {
        val toLaunch: Set<LocalBlockchainInfo> = retrieveBlockchainsToLaunch()
        val chainIdsToLaunch: Set<Long> = toLaunch.map { it.chainId }.toSet()
        val masterLaunched: Set<Long> = getLaunchedBlockchains()
        val subnodeLaunched: Set<Long> = getStartingOrRunningContainerBlockchains()

        // Chain0
        if (reloadChain0) {
            logger.debug("[${nodeName()}]: ContainerJob -- Restart chain0")
            startBlockchainAsync(CHAIN0, null)
        }

        // Stopping launched blockchains
        masterLaunched.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug("[${nodeName()}]: ContainerJob -- Stop system chain: $it")
            stopBlockchainAsync(it, null)
        }
        subnodeLaunched.filterNot(chainIdsToLaunch::contains).forEach {
            logger.debug("[${nodeName()}]: ContainerJob -- Stop subnode chain: ${getChain(it)}")
            containerJobManager.stopChain(getChain(it))
        }

        // Launching new blockchains except blockchain 0
        toLaunch.filter { it.chainId != CHAIN0 && it.chainId !in masterLaunched && it.chainId !in subnodeLaunched }.forEach {
            if (it.system) {
                logger.debug("[${nodeName()}]: ContainerJob -- Start system chain: ${it.chainId}")
                startBlockchainAsync(it.chainId, null)
            } else {
                logger.debug("[${nodeName()}]: ContainerJob -- Start subnode chain: ${getChain(it.chainId)}")
                containerJobManager.startChain(getChain(it.chainId))
            }
        }
    }

    private fun scheduleDockerContainersHealthcheck() {
        val period = containerNodeConfig.healthcheckRunningContainersCheckPeriod.toLong()
        if (period > 0 && postchainContainers.isNotEmpty()) {
            val height = withReadConnection(storage, CHAIN0) { ctx ->
                DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
            }

            if (height % period == 0L) {
                logger.debug("[${nodeName()}]: ContainerJob -- Healthcheck job created")
                containerJobManager.doHealthcheck()
            }
        }
    }

    private fun containerJobHandler(job: ContainerJob) {
        val scope = "ContainerJobHandler"
        logger.info {
            "[${nodeName()}]: $scope -- Job for container will be handled: " +
                    "containerName: ${job.containerName}, " +
                    "chains to stop: ${job.chainsToStop.map { it.chainId }.toTypedArray().contentToString()}, " +
                    "chains to start: ${job.chainsToStart.map { it.chainId }.toTypedArray().contentToString()}"
        }

        fun result(result: Boolean) {
            val msg = when (result) {
                true -> "Job for container ${job.containerName} has been finished successfully"
                false -> "Job for container ${job.containerName} hasn't been finished yet and will be postponed"
            }
            logger.info { "[${nodeName()}]: $scope -- $msg" }
        }

        // 1. Create PostchainContainer
        var psContainer = postchainContainers[job.containerName]
        val dockerContainer = findDockerContainer(job.containerName)

        if (psContainer == null) {
            logger.debug { "[${nodeName()}]: $scope -- PostchainContainer not found and will be created" }

            // Finding available/existent host ports
            val containerPorts = ContainerPorts(containerNodeConfig)
            val hostPorts = dockerClient.findHostPorts(dockerContainer, containerPorts.getPorts())
            containerPorts.setHostPorts(hostPorts)
            if (!containerPorts.verify()) {
                logger.error { "[${nodeName()}]: $scope -- Can't finding available/existent host ports" }
                return result(false)
            }

            // Building PostchainContainer
            val subnodeAdminClient = SubnodeAdminClient.create(containerNodeConfig, containerPorts)
            psContainer = DefaultPostchainContainer(
                    directoryDataSource, job.containerName, containerPorts, STARTING, subnodeAdminClient)
            logger.debug { "[${nodeName()}]: $scope -- PostchainContainer created" }
            val dir = initContainerWorkingDir(fs, psContainer)
            if (dir != null) {
                postchainContainers[psContainer.containerName] = psContainer
                logger.debug { "[${nodeName()}]: $scope -- Container dir initialized, container: ${job.containerName}, dir: $dir" }
            } else {
                // error
                logger.error { "[${nodeName()}]: $scope -- Container dir hasn't been initialized, container: ${job.containerName}" }
                //return false
                return result(false)
            }
        }

        // 2. Start Docker container
        val dcLog = { state: String, container: PostchainContainer? ->
            "[${nodeName()}]: $scope -- Docker container $state: ${job.containerName}, " +
                    "containerId: ${container?.shortContainerId()}"
        }
        if (dockerContainer == null && job.chainsToStart.isNotEmpty()) {
            logger.debug { dcLog("not found", null) }

            // creating container
            val config = ContainerConfigFactory.createConfig(fs, appConfig, containerNodeConfig, psContainer)
            psContainer.containerId = dockerClient.createContainer(config, job.containerName.toString()).id()!!
            logger.debug { dcLog("created", psContainer) }

            // starting container
            dockerClient.startContainer(psContainer.containerId)
            psContainer.start()
            logger.info { dcLog("started", psContainer) }

            job.postpone(5_000)
            return result(false)
        }

        if (dockerContainer != null && dockerContainer.state() == "exited" && job.chainsToStart.isNotEmpty()) {
            logger.info { dcLog("stopped and will be started", psContainer) }
            psContainer.containerId = dockerContainer.id()
            dockerClient.startContainer(psContainer.containerId)
            psContainer.start()
            job.postpone(5_000)
            return result(false)
        }

        // 3. Asserting subnode is connected and running
        if (dockerContainer != null && job.chainsToStart.isNotEmpty()) {
            if (!psContainer.isSubnodeConnected()) {
                logger.warn { "[${nodeName()}]: $scope -- Subnode is not connected, container: ${job.containerName}" }
                job.postpone(5_000)
                return result(false)
            } else {
                logger.info { "[${nodeName()}]: $scope -- Subnode is connected, container: ${job.containerName}" }
            }
        } else {
            logger.debug { "[${nodeName()}]: $scope -- DockerContainer is not running, 'is subnode connected' check will be skipped, container: ${job.containerName}" }
        }

        // 4. Stop chains
        job.chainsToStop.forEach { chain ->
            val process = terminateBlockchainProcess(chain.chainId, psContainer)
            logger.debug { "[${nodeName()}]: $scope -- ContainerBlockchainProcess terminated: $process" }
            logger.info { "[${nodeName()}]: $scope -- Blockchain stopped: ${chain.chainId} / ${chain.brid.toShortHex()} " }
        }

        // 5. Start chains
        job.chainsToStart.forEach { chain ->
            val process = createBlockchainProcess(chain, psContainer)
            logger.debug { "[${nodeName()}]: $scope -- ContainerBlockchainProcess created: $process" }
            logger.info { "[${nodeName()}]: $scope -- Blockchain started: ${chain.chainId} / ${chain.brid.toShortHex()} " }
        }

        // 6. Stop container if it is empty
        if (psContainer.isEmpty()) {
            logger.info { "[${nodeName()}]: $scope -- Container is empty and will be stopped: ${job.containerName}" }
            psContainer.stop()
            postchainContainers.remove(psContainer.containerName)
            if (dockerContainer != null) {
                dockerClient.stopContainer(dockerContainer.id(), 10)
                logger.debug { "[${nodeName()}]: $scope -- Docker container stopped: ${job.containerName}" }
            }
            logger.info { "[${nodeName()}]: $scope -- Container stopped: ${job.containerName}" }
        }

        job.done = true
        return result(true)
    }

    private fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path? =
            fs.createContainerRoot(container.containerName, container.resourceLimits)

    private fun containerHealthcheckJobHandler(containersInProgress: Set<String>) {
        val start = System.currentTimeMillis()
        val scope = "ContainerHealthcheckJobHandler"
        logger.debug { "[${nodeName()}]: $scope -- BEGIN" }

        val containersToCheck = postchainContainers.keys
                .associateBy { it.name }
                .filter { it.key !in containersInProgress }

        logger.info {
            "[${nodeName()}]: $scope -- containersInProgress: $containersInProgress, containersToCheck: ${containersToCheck.keys}"
        }

        val fixed = mutableSetOf<ContainerName>()
        if (containersToCheck.isNotEmpty()) {
            val running = dockerClient.listContainers() // running containers only
            containersToCheck.values.forEach { cname ->
                val psContainer = postchainContainers[cname]!!
                val dc = running.firstOrNull { it.hasName(cname.name) }
                val chainIds = if (dc == null) {
                    logger.warn { "[${nodeName()}]: $scope -- Docker container is not running and will be restarted: ${cname.name}" }
                    fixed.add(cname)
                    psContainer.getAllChains().toSet()
                } else {
                    logger.debug { "[${nodeName()}]: $scope -- Docker container is running: ${cname.name}" }
                    psContainer.getStoppedChains().toSet()
                }

                chainIds.forEach {
                    terminateBlockchainProcess(it, psContainer)
                }
                if (chainIds.isNotEmpty()) {
                    logger.warn { "[${nodeName()}]: $scope -- Container chains have been terminated: $chainIds" }
                }

                if (psContainer.isEmpty()) {
                    psContainer.stop()
                    postchainContainers.remove(cname)
                }
            }
        }

        if (fixed.isEmpty()) {
            logger.info { "[${nodeName()}]: $scope -- Ok" }
        } else {
            logger.warn { "[${nodeName()}]: $scope -- Fixed: $fixed" }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "[${nodeName()}]: $scope -- END ($elapsed ms)" }
    }

    override fun shutdown() {
        getStartingOrRunningContainerBlockchains()
                .forEach { stopBlockchain(it, bTrace = null) }
        containerJobManager.shutdown()
        super.shutdown()
    }

    private fun createBlockchainProcess(chain: Chain, psContainer: PostchainContainer): ContainerBlockchainProcess? {
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                BlockchainProcessName(appConfig.pubKey, chain.brid),
                chain.chainId,
                chain.brid,
                directoryDataSource,
                psContainer
        )

        val started = psContainer.startProcess(process)
        if (started) {
            chainIdToBrid[chain.chainId] = chain.brid
            bridToChainId[chain.brid] = chain.chainId
            extensions.filterIsInstance<RemoteBlockchainProcessConnectable>()
                    .forEach { it.connectRemoteProcess(process) }
            blockchainProcessesDiagnosticData[chain.brid] = mutableMapOf(
                    DiagnosticProperty.BLOCKCHAIN_RID to { process.blockchainRid.toHex() },
                    DiagnosticProperty.BLOCKCHAIN_CURRENT_HEIGHT to { psContainer.getBlockchainLastHeight(process.chainId) },
                    DiagnosticProperty.CONTAINER_NAME to { psContainer.containerName.toString() },
                    DiagnosticProperty.CONTAINER_ID to { psContainer.shortContainerId() ?: "" }
            )
        }

        return process.takeIf { started }
    }

    private fun terminateBlockchainProcess(chainId: Long, psContainer: PostchainContainer): ContainerBlockchainProcess? {
        return psContainer.terminateProcess(chainId)
                ?.also { process ->
                    extensions.filterIsInstance<RemoteBlockchainProcessConnectable>()
                            .forEach { it.disconnectRemoteProcess(process) }
                    masterBlockchainInfra.exitMasterBlockchainProcess(process)
                    val blockchainRid = chainIdToBrid.remove(chainId)
                    blockchainProcessesDiagnosticData.remove(blockchainRid)
                    bridToChainId.remove(blockchainRid)
                    chains.remove(chainId)
                    process.shutdown()
                }
    }

    private fun findDockerContainer(containerName: ContainerName): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.firstOrNull { it.hasName(containerName.name) }
    }

    private fun getStartingOrRunningContainerBlockchains(): Set<Long> {
        return postchainContainers.values
                .filter { it.state == STARTING || it.state == RUNNING }
                .flatMap { it.getAllChains() }
                .toSet()
    }

    private fun getChain(chainId: Long): Chain {
        return chains.computeIfAbsent(chainId) {
            val brid = getBridByChainId(chainId)
            val container = directoryDataSource.getContainerForBlockchain(brid)
            val containerName = ContainerName.create(appConfig, container)
            Chain(containerName, chainId, brid)
        }
    }

    private fun getBridByChainId(chainId: Long): BlockchainRid {
        return withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
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
                dockerClient.removeContainer(it.id())
                logger.info { "Container has been removed: ${containerName(it)} / ${shortContainerId(it.id())}" }
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
