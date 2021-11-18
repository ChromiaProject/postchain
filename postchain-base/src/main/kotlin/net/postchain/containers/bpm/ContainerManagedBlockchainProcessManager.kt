package net.postchain.containers.bpm

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerState.RUNNING
import net.postchain.containers.bpm.ContainerState.STARTING
import net.postchain.containers.bpm.DockerTools.checkContainerName
import net.postchain.containers.bpm.DockerTools.containerName
import net.postchain.containers.bpm.DockerTools.shortContainerId
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainRid
import net.postchain.core.RestartHandler
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedBlockchainProcessManager

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

    private val directoryDataSource: DirectoryDataSource by lazy { dataSource as DirectoryDataSource }
    private val chains: MutableMap<Long, Chain> = mutableMapOf() // chainId -> Chain

    /**
     * TODO: [POS-129]: Implement handling of DockerException
     */
    private val containerInitializer = DefaultContainerInitializer(nodeConfig)
    private val dockerClient: DockerClient = DockerClientFactory.create()
    private val postchainContainers = mutableSetOf<PostchainContainer>()
    private val containerJobManager = DefaultContainerJobManager(::containerJobHandler, ::containerHealthcheckJobHandler)

    override fun initManagedEnvironment() {
        super.initManagedEnvironment()
        stopRunningChainContainers()
    }

    override fun createDataSource(blockQueries: BlockQueries) =
            BaseDirectoryDataSource(blockQueries, nodeConfigProvider.getConfiguration())

    override fun buildRestartHandler(chainId: Long): RestartHandler {
        return { blockTimestamp: Long, blockTrace: BlockTrace? ->
            try {
                rTrace("Before", chainId, blockTrace)
                if (chainId != CHAIN0) {
                    logger.warn {
                        "[${nodeName()}]: RestartHandler() -- RestartHandler has been called for chainId $chainId != 0, " +
                                "block causing handler to run: $blockTrace"
                    }
                    false
                } else {
                    synchronized(synchronizer) {
                        rTrace("Sync block / before", chainId, blockTrace)
                        // Sending heartbeat to other chains
                        heartbeatManager.beat(blockTimestamp)

                        // Preloading blockchain configuration
                        preloadChain0Configuration()

                        // Checking out the peer list changes
                        val doReload = isPeerListChanged()

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
                            executeDockerContainersHealthcheck()
                            res2
                        }

                        rTrace("Sync block / after", chainId, blockTrace)
                        res
                    }
                }
            } catch (e: Exception) {
                logger.error("Exception in RestartHandler: $e")
                e.printStackTrace()
                restartBlockchainAsync(chainId, blockTrace)
                true // let's hope restarting a blockchain fixes the problem
            }
        }
    }

    /**
     * Restart all chains. Begin with chain zero.
     */
    private fun reloadAllBlockchains() {
        restartBlockchainAsync(CHAIN0, null)

        postchainContainers.forEach { cont ->
            cont.getChains().forEach {
                logger.debug("[${nodeName()}]: ContainerJob -- restart chain: ${getChain(it)}")
                containerJobManager.restartChain(getChain(it))
            }
        }
    }

    private fun stopStartBlockchains(reloadChain0: Boolean) {
        val toLaunch = retrieveBlockchainsToLaunch()
        val launched = getLaunchedBlockchains()

        // Chain0
        if (reloadChain0) {
            logger.debug("[${nodeName()}]: ContainerJob -- Restart chain0")
            restartBlockchainAsync(CHAIN0, null)
        }

        // Stopping launched blockchains
        launched.filterNot(toLaunch::contains).forEach {
            logger.debug("[${nodeName()}]: ContainerJob -- Stop chain: ${getChain(it)}")
            containerJobManager.stopChain(getChain(it))
        }

        // Launching new blockchains except blockchain 0
        toLaunch.filter { it != CHAIN0 && it !in launched }.forEach {
            logger.debug("[${nodeName()}]: ContainerJob -- Start chain: ${getChain(it)}")
            containerJobManager.startChain(getChain(it))
        }
    }

    private fun containerJobHandler(containerName: ContainerName, chainsToStop: Set<Chain>, chainsToStart: Set<Chain>) {
        val start = System.currentTimeMillis()
        logger.debug {
            "[${nodeName()}]: ContainerJobHandler -- Begin: " +
                    "containerName: $containerName, " +
                    "chains to stop: ${chainsToStop.map { it.chainId }.toTypedArray().contentToString()}, " +
                    "chains to start: ${chainsToStart.map { it.chainId }.toTypedArray().contentToString()}"
        }

        // Postchain container
        var psContainer = findPostchainContainer(containerName.name)
        var restartDockerContainer = false

        if (psContainer == null) {
            chainsToStop.forEach { chain ->
                if (clearBlockchainProcessResources(chain)) { // TODO: [POS-164]: Redesign
                    logger.debug { "[${nodeName()}]: ContainerJobHandler -- chain resources cleared: $chain" }
                    restartDockerContainer = true
                }
            }

            if (chainsToStart.isNotEmpty()) {
                logger.debug { "[${nodeName()}]: ContainerJobHandler -- PostchainContainer created" }
                val newPsContainer = DefaultPostchainContainer(nodeConfig, directoryDataSource, containerName, STARTING)
                containerInitializer.initContainerWorkingDir(newPsContainer)
                postchainContainers.add(newPsContainer)

                chainsToStart.forEach { chain ->
                    val process = createBlockchainProcess(chain, newPsContainer)
                    logger.debug { "[${nodeName()}]: ContainerJobHandler -- BlockchainProcess created: $process" }
                }

                psContainer = newPsContainer
                restartDockerContainer = true
            }
        } else {
            chainsToStop.forEach { chain ->
                val (process, res) = terminateBlockchainProcess(psContainer, chain)
                if (res) {
                    logger.debug { "[${nodeName()}]: ContainerJobHandler -- ContainerBlockchainProcess terminated: $process" }
                    restartDockerContainer = true
                }
            }

            chainsToStart.forEach { chain ->
                val process = createBlockchainProcess(chain, psContainer)
                logger.debug { "[${nodeName()}]: ContainerJobHandler -- BlockchainProcess created: $process" }
                restartDockerContainer = true
            }
        }

        // Docker container
        if (restartDockerContainer) {
            val msg = { state: String, container: PostchainContainer? ->
                "[${nodeName()}]: ContainerJobHandler -- Docker container $state: $containerName, " +
                        "containerId: ${container?.shortContainerId()}"
            }

            val dockerContainer = findDockerContainer(containerName)
            if (dockerContainer == null) {
                logger.debug { msg("not found", null) }

                if (!psContainer!!.isEmpty()) {
                    val containerDir = containerInitializer.getContainerWorkingDir(containerName)
                    val config = ContainerConfigFactory.createConfig(nodeConfig, psContainer, containerDir.toString())

                    psContainer.containerId = dockerClient.createContainer(config, containerName.toString()).id()!!
                    logger.debug { msg("created", psContainer) }

                    dockerClient.startContainer(psContainer.containerId)
                    psContainer.start()
                    logger.debug { msg("started", psContainer) }
                }
            } else {
                psContainer!!.containerId = dockerContainer.id()
                logger.debug { msg("found", psContainer) }
                if (psContainer.isEmpty()) {
                    psContainer.stop()
                    postchainContainers.remove(psContainer)
                    dockerClient.stopContainer(dockerContainer.id(), 10)
                    logger.debug { msg("stopped", psContainer) }
                } else {
                    dockerClient.restartContainer(dockerContainer.id())
                    logger.debug { msg("restarted", psContainer) }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "[${nodeName()}]: ContainerJobHandler -- End ($elapsed ms)" }
    }

    private fun executeDockerContainersHealthcheck() {
        val period = nodeConfig.runningContainersCheckPeriod.toLong()
        if (period > 0 && postchainContainers.isNotEmpty()) {
            val height = withReadConnection(storage, CHAIN0) { ctx ->
                DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
            }

            if (height % period == 0L) {
                logger.debug("[${nodeName()}]: ContainerJob -- Healthcheck job created")
                containerJobManager.executeHealthcheck()
            }
        }
    }

    private fun containerHealthcheckJobHandler() {
        val start = System.currentTimeMillis()
        logger.debug { "[${nodeName()}]: ContainerHealthcheckJobHandler -- Begin" }

        val running = dockerClient.listContainers() // running containers only
        postchainContainers.forEach { psc ->
            val dc = running.find { checkContainerName(it, psc.containerName) }
            if (dc == null && psc.containerId != null) {
                logger.warn { "Container ${psc.containerName} / ${psc.shortContainerId()} is not running and will be restarted" }
                dockerClient.startContainer(psc.containerId)
                logger.info { "Container ${psc.containerName} / ${psc.shortContainerId()} has been restarted" }
            }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "[${nodeName()}]: ContainerHealthcheckJobHandler -- End ($elapsed ms)" }
    }

    override fun shutdown() {
        startingOrRunningContainerProcesses()
                .forEach { stopBlockchain(it, bTrace = null) }
        containerJobManager.shutdown()
        super.shutdown()
    }

    private fun logChain(caller: String, process: ContainerBlockchainProcess, cont: PostchainContainer) {
        val message = "$caller: " +
                "chainId: ${process.chainId}, " +
                "blockchainRid: ${process.blockchainRid}, " +
                "containerName: ${cont.containerName}, " +
                "containerId: ${cont.containerId}"
        logger.info("\t" + message) // \t -- for tests
    }

    private fun createBlockchainProcess(chain: Chain, targetContainer: PostchainContainer): ContainerBlockchainProcess {
        val containerChainDir = containerInitializer.initContainerChainWorkingDir(chain)
        val processName = BlockchainProcessName(nodeConfig.pubKey, chain.brid)
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                processName,
                chain.chainId,
                chain.brid,
                directoryDataSource,
                containerChainDir,
                nodeConfig.subnodeRestApiPort
        )
        process.transferConfigsToContainer()
        targetContainer.addProcess(process)
        heartbeatManager.addListener(process)
        return process
    }

    private fun terminateBlockchainProcess(
            container: PostchainContainer,
            chain: Chain
    ): Pair<ContainerBlockchainProcess?, Boolean> {
        val process = container.findProcesses(chain.chainId)
        return if (process != null) {
            heartbeatManager.removeListener(process)
            container.removeProcess(process)
            process to containerInitializer.rmChainWorkingDir(chain) // TODO: [POS-164]: Redesign
        } else {
            null to false
        }
    }

    private fun clearBlockchainProcessResources(chain: Chain): Boolean {
        return containerInitializer.rmChainWorkingDir(chain)
    }

    private fun findDockerContainer(containerName: ContainerName): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.find { checkContainerName(it, containerName) }
    }

    private fun findPostchainContainer(containerName: String) =
            postchainContainers.find { it.containerName.name == containerName }

    override fun getLaunchedBlockchains(): Set<Long> {
        // chain0 + chainsOf(starting/running containers)
        return super.getLaunchedBlockchains() + startingOrRunningContainerProcesses()
    }

    private fun startingOrRunningContainerProcesses(): Set<Long> {
        return postchainContainers
                .filter { it.state == STARTING || it.state == RUNNING }
                .flatMap { it.getChains() }
                .toSet()
    }

    private fun getBridByChainId(chainId: Long): BlockchainRid {
        return withReadConnection(storage, chainId) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
    }

    private fun getChain(chainId: Long): Chain {
        return chains.computeIfAbsent(chainId) {
            val brid = getBridByChainId(chainId)
            val container = directoryDataSource.getContainerForBlockchain(brid)
            val containerName = ContainerName.create(nodeConfig, container)
            Chain(containerName, chainId, brid)
        }
    }

    private fun stopRunningChainContainers() {
        if (nodeConfig.runningContainersAtStartRegexp.isNotEmpty()) {
            val toStop = dockerClient.listContainers().filter {
                try {
                    containerName(it).contains(Regex(nodeConfig.runningContainersAtStartRegexp))
                } catch (e: Exception) {
                    logger.error { "Regexp expression error: ${nodeConfig.runningContainersAtStartRegexp}" }
                    false
                }
            }

            if (toStop.isNotEmpty()) {
                logger.warn {
                    "Containers found to be stopped (${toStop.size}): ${toStop.joinToString(transform = ::containerName)}"
                }

                toStop.forEach {
                    dockerClient.stopContainer(it.id(), 20)
                    logger.info { "Container has been stopped: ${containerName(it)} / ${shortContainerId(it.id())}" }
                }
            }
        }
    }
}
