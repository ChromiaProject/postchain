package net.postchain.containers.bpm

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.ContainerState.RUNNING
import net.postchain.containers.bpm.ContainerState.STARTING
import net.postchain.containers.bpm.DockerTools.containerName
import net.postchain.containers.bpm.DockerTools.findHostPorts
import net.postchain.containers.bpm.DockerTools.hasName
import net.postchain.containers.bpm.DockerTools.shortContainerId
import net.postchain.containers.bpm.rpc.ContainerJob
import net.postchain.containers.bpm.rpc.ContainerPorts
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.AfterCommitHandler
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedBlockchainProcessManager

open class ContainerManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        private val masterBlockchainInfra: MasterBlockchainInfra,
        blockchainConfigProvider: BlockchainConfigurationProvider,
) : ManagedBlockchainProcessManager(
        postchainContext,
        masterBlockchainInfra,
        blockchainConfigProvider
) {

    companion object : KLogging()

    private val directoryDataSource: DirectoryDataSource by lazy { dataSource as DirectoryDataSource }
    private val chains: MutableMap<Long, Chain> = mutableMapOf() // chainId -> Chain

    /**
     * TODO: [POS-129]: Implement handling of DockerException
     */
    private val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
    private val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
    private val fs = FileSystem.create(containerNodeConfig)
    private val containerInitializer = DefaultContainerInitializer(appConfig, containerNodeConfig)
    private val dockerClient: DockerClient = DockerClientFactory.create()
    private val postchainContainers = mutableMapOf<ContainerName, PostchainContainer>() // { ContainerName -> PsContainer }

    //    private val containerJobManager = DefaultContainerJobManager(::containerJobHandler, ::containerHealthcheckJobHandler)
    private val containerJobManagerRpc = net.postchain.containers.bpm.rpc.DefaultContainerJobManager(::containerJobHandlerRpc, ::containerHealthcheckJobHandler)

    override fun initManagedEnvironment() {
        super.initManagedEnvironment()
        stopRunningContainersIfExist()
    }

    override fun createDataSource(blockQueries: BlockQueries) = BaseDirectoryDataSource(blockQueries, appConfig, containerNodeConfig)

    override fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {
        return { blockTrace: BlockTrace?, _, blockTimestamp: Long ->
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
                        val peerListVersion = dataSource.getPeerListVersion()
                        val doReload = (this.peerListVersion != peerListVersion)
                        this.peerListVersion = peerListVersion

                        val res = containerJobManagerRpc.withLock {
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

        postchainContainers.values.forEach { cont ->
            cont.getAllChains().forEach {
                logger.debug("[${nodeName()}]: ContainerJob -- restart chain: ${getChain(it)}")
                containerJobManagerRpc.restartChain(getChain(it))
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
            containerJobManagerRpc.stopChain(getChain(it))
        }

        // Launching new blockchains except blockchain 0
        toLaunch.filter { it != CHAIN0 && it !in launched }.forEach {
            logger.debug("[${nodeName()}]: ContainerJob -- Start chain: ${getChain(it)}")
            containerJobManagerRpc.startChain(getChain(it))
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
                containerJobManagerRpc.doHealthcheck()
            }
        }
    }

    /*
    @Deprecated("Use RPC version instead")
    private fun containerJobHandler(containerName: ContainerName, chainsToStop: Set<Chain>, chainsToStart: Set<Chain>) {
        val start = System.currentTimeMillis()
        val scope = "ContainerJobHandler"
        logger.debug {
            "[${nodeName()}]: $scope -- Begin: " +
                    "containerName: $containerName, " +
                    "chains to stop: ${chainsToStop.map { it.chainId }.toTypedArray().contentToString()}, " +
                    "chains to start: ${chainsToStart.map { it.chainId }.toTypedArray().contentToString()}"
        }

        /**
         * Step 1: Postchain Container
         */
        var restartDockerContainer = false
        var psContainer = postchainContainers[containerName]
        val dockerContainer = findDockerContainer(containerName.name)

        if (psContainer == null) {
            chainsToStop.forEach { chain ->
                if (clearBlockchainProcessResources(chain)) { // TODO: [POS-164]: Redesign
                    logger.debug { "[${nodeName()}]: $scope -- chain resources cleared: $chain" }
                    restartDockerContainer = true
                }
            }

            if (chainsToStart.isNotEmpty()) {
                logger.debug { "[${nodeName()}]: $scope -- PostchainContainer created" }
                // Finding available/existent host ports
                val containerPorts = ContainerPorts(containerNodeConfig)
                val hostPorts = dockerClient.findHostPorts(dockerContainer, containerPorts.getPorts())
                containerPorts.setHostPorts(hostPorts)
                if (!containerPorts.verify()) {
                    logger.error { }
                }

                // Building PostchainContainer
                val subnodeAdminClient = SubnodeAdminClient.create(containerNodeConfig, containerPorts)
                val newPsContainer = DefaultPostchainContainer(
                        directoryDataSource, containerName, containerPorts, STARTING, subnodeAdminClient)
                val dir = containerInitializer.initContainerWorkingDir(fs, newPsContainer)
                if (dir != null) {
                    postchainContainers[newPsContainer.containerName] = newPsContainer
                    logger.debug {
                        "[${nodeName()}]: $scope -- Container dir inited, container: $containerName, dir: $dir"
                    }

                    chainsToStart.forEach { chain ->
                        val process = createBlockchainProcess(chain, newPsContainer)
                        if (process != null) {
                            logger.debug { "[${nodeName()}]: $scope -- BlockchainProcess created: $process" }
                        } else {
                            logger.error { "[${nodeName()}]: $scope -- Can't create BlockchainProcess for chain $chain" }
                        }
                    }

                    psContainer = newPsContainer
                    restartDockerContainer = true

                } else {
                    logger.error {
                        "[${nodeName()}]: $scope -- Container dir hasn't been inited, container: $containerName, dir: $dir"
                    }
                }
            }
        } else { // psContainer != null
            chainsToStop.forEach { chain ->
                val (process, res) = terminateBlockchainProcess(chain, psContainer)
                if (res) {
                    logger.debug { "[${nodeName()}]: $scope -- ContainerBlockchainProcess terminated: $process" }
                    restartDockerContainer = true
                }
            }

            chainsToStart.forEach { chain ->
                val process = createBlockchainProcess(chain, psContainer)
                if (process != null) {
                    logger.debug { "[${nodeName()}]: $scope -- BlockchainProcess created: $process" }
                    restartDockerContainer = true
                } else {
                    logger.error { "[${nodeName()}]: $scope -- Can't create BlockchainProcess for chain $chain" }
                }
            }
        }

        /**
         * Step 2: Docker Container
         */
        if (restartDockerContainer) {
            val msg = { state: String, container: PostchainContainer? ->
                "[${nodeName()}]: $scope -- Docker container $state: $containerName, " +
                        "containerId: ${container?.shortContainerId()}"
            }

            if (dockerContainer == null) {
                logger.debug { msg("not found", null) }

                if (!psContainer!!.isEmpty()) {
                    val config = ContainerConfigFactory.createConfig(fs, restApiConfig, containerNodeConfig, psContainer)

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
                    postchainContainers.remove(psContainer.containerName)
                    dockerClient.stopContainer(dockerContainer.id(), 10)
                    logger.debug { msg("stopped", psContainer) }
                } else {
                    dockerClient.restartContainer(dockerContainer.id())
                    logger.debug { msg("restarted", psContainer) }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.debug { "[${nodeName()}]: $scope -- End ($elapsed ms)" }
    }
     */

    private fun containerJobHandlerRpc(job: ContainerJob) {
        val scope = "ContainerJobHandler"
        logger.error {
            "[${nodeName()}]: $scope -- BEGIN: " +
                    "containerName: ${job.containerName}, " +
                    "chains to stop: ${job.chainsToStop.map { it.chainId }.toTypedArray().contentToString()}, " +
                    "chains to start: ${job.chainsToStart.map { it.chainId }.toTypedArray().contentToString()}"
        }

        fun result(result: Boolean) {
            val msg = when (result) {
                true -> "END: Job for container ${job.containerName} has been finished successfully"
                false -> "END: Job for container ${job.containerName} hasn't been finished yet and will be postponed"
            }
            logger.error { "[${nodeName()}]: $scope -- $msg" }
        }

        // 1. Create PostchainContainer
        var psContainer = postchainContainers[job.containerName]
        val dockerContainer = findDockerContainer(job.containerName)

        if (psContainer == null) {
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
            logger.error { "[${nodeName()}]: $scope -- PostchainContainer created" }
            val dir = containerInitializer.initContainerWorkingDir(fs, psContainer)
            if (dir != null) {
                postchainContainers[psContainer.containerName] = psContainer
                logger.error { "[${nodeName()}]: $scope -- Container dir initialized, container: ${job.containerName}, dir: $dir" }
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
            logger.error { dcLog("not found", null) }

            // creating container
            val config = ContainerConfigFactory.createConfig(fs, restApiConfig, containerNodeConfig, psContainer)
            psContainer.containerId = dockerClient.createContainer(config, job.containerName.toString()).id()!!
            logger.error { dcLog("created", psContainer) }

            // starting container
            dockerClient.startContainer(psContainer.containerId)
            psContainer.start()
            logger.debug { dcLog("started", psContainer) }

            job.postpone(5_000)
            return result(false)
        }

        if (dockerContainer != null && dockerContainer.state() == "exited" && job.chainsToStart.isNotEmpty()) {
            logger.error { dcLog("stopped and will be started", psContainer) }
            psContainer.containerId = dockerContainer.id()
            dockerClient.startContainer(psContainer.containerId)
            psContainer.start()
            job.postpone(5_000)
            return result(false)
        }

        // 3. Asserting subnode is connected and running
        if (dockerContainer != null && job.chainsToStart.isNotEmpty()) {
            if (!psContainer.isSubnodeConnected()) {
                logger.error { "[${nodeName()}]: $scope -- Subnode is not connected, container: ${job.containerName}" }
                return result(false)
            } else {
                logger.error { "[${nodeName()}]: $scope -- Subnode is connected, container: ${job.containerName}" }
            }
        } else {
            logger.error { "[${nodeName()}]: $scope -- DockerContainer is not running, 'is subnode connected' check will be skipped, container: ${job.containerName}" }
        }

        // 4. Stop chains
        job.chainsToStop.forEach { chain ->
            val process = terminateBlockchainProcessRpc(chain.chainId, psContainer)
            logger.error { "[${nodeName()}]: $scope -- ContainerBlockchainProcess terminated: $process" }
        }

        // 5. Start chains
        job.chainsToStart.forEach { chain ->
            val process = createBlockchainProcessRpc(chain, psContainer)
            logger.error { "[${nodeName()}]: $scope -- ContainerBlockchainProcess created: $process" }
        }

        // 6. Stop container if it is empty
        if (psContainer.isEmpty()) {
            logger.error { "[${nodeName()}]: $scope -- Container is empty and will be stopped: ${job.containerName}" }
            psContainer.stop()
            postchainContainers.remove(psContainer.containerName)
            if (dockerContainer != null) {
                dockerClient.stopContainer(dockerContainer.id(), 10)
                logger.error { "[${nodeName()}]: $scope -- Docker container stopped: ${job.containerName}" }
            }
            logger.error { "[${nodeName()}]: $scope -- Container stopped: ${job.containerName}" }
        }

        job.done = true
        return result(true)
    }

    private fun containerHealthcheckJobHandler(containersInProgress: Set<String>) {
        val start = System.currentTimeMillis()
        val scope = "ContainerHealthcheckJobHandler"
        logger.error { "[${nodeName()}]: $scope -- BEGIN" }

        val containersToCheck = postchainContainers.keys
                .associateBy { it.name }
                .filter { it.key !in containersInProgress }

        logger.error {
            "[${nodeName()}]: $scope -- containersInProgress: $containersInProgress, containersToCheck: ${containersToCheck.keys}"
        }

        if (containersToCheck.isNotEmpty()) {
            val running = dockerClient.listContainers() // running containers only
            containersToCheck.values.forEach { cname ->
                val psContainer = postchainContainers[cname]!!
                val dc = running.firstOrNull { it.hasName(cname.name) }
                val chainIds = if (dc == null) {
                    logger.error { "[${nodeName()}]: $scope -- Docker container is not running and will be restarted: ${cname.name}" }
                    psContainer.getAllChains().toSet()
                } else {
                    logger.error { "[${nodeName()}]: $scope -- Docker container is running: ${cname.name}" }
                    psContainer.getStoppedChains().toSet()
                }

                chainIds.forEach {
                    terminateBlockchainProcessRpc(it, psContainer)
                }
                if (chainIds.isNotEmpty()) {
                    logger.error { "[${nodeName()}]: $scope -- Container chains have been terminated: $chainIds" }
                }

                if (psContainer.isEmpty()) {
                    psContainer.stop()
                    postchainContainers.remove(cname)
                }
            }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.error { "[${nodeName()}]: $scope -- END ($elapsed ms)" }
    }

    override fun shutdown() {
        getStartingOrRunningContainerBlockchains()
                .forEach { stopBlockchain(it, bTrace = null) }
        containerJobManagerRpc.shutdown()
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

    private fun createBlockchainProcess(chain: Chain, targetContainer: PostchainContainer): ContainerBlockchainProcess? {
        val dir = containerInitializer.initContainerChainWorkingDir(fs, chain)
        return if (dir != null) {
            val processName = BlockchainProcessName(appConfig.pubKey, chain.brid)
            val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                    processName,
                    chain.chainId,
                    chain.brid,
                    directoryDataSource,
                    targetContainer,
                    dir
            ).also {
                blockchainProcessesDiagnosticData[chain.brid] = mutableMapOf(
                        DiagnosticProperty.BLOCKCHAIN_RID to { it.blockchainRid.toHex() },
                        DiagnosticProperty.CONTAINER_NAME to { targetContainer.containerName.toString() },
                        DiagnosticProperty.CONTAINER_ID to { targetContainer.shortContainerId() ?: "" }
                )
                chainIdToBrid[chain.chainId] = chain.brid
            }
            process.transferConfigsToContainer()
            targetContainer.addProcess(process)
            heartbeatManager.addListener(chain.chainId, process)
            process
        } else {
            null
        }
    }

    private fun createBlockchainProcessRpc(chain: Chain, psContainer: PostchainContainer): ContainerBlockchainProcess? {
        val process = masterBlockchainInfra.makeMasterBlockchainProcess(
                BlockchainProcessName(appConfig.pubKey, chain.brid),
                chain.chainId,
                chain.brid,
                directoryDataSource,
                psContainer,
                null // TODO: POS-301: Delete it
        )

        val started = psContainer.startProcess(process)
        if (started) {
            heartbeatManager.addListener(chain.chainId, process)
            chainIdToBrid[chain.chainId] = chain.brid
            blockchainProcessesDiagnosticData[chain.brid] = mutableMapOf(
                    DiagnosticProperty.BLOCKCHAIN_RID to { process.blockchainRid.toHex() },
                    DiagnosticProperty.CONTAINER_NAME to { psContainer.containerName.toString() },
                    DiagnosticProperty.CONTAINER_ID to { psContainer.shortContainerId() ?: "" }
            )
        }

        return process.takeIf { started }
    }

    private fun terminateBlockchainProcess(chain: Chain, container: PostchainContainer): Pair<ContainerBlockchainProcess?, Boolean> {
        val process = container.findProcesses(chain.chainId)
        return if (process != null) {
            masterBlockchainInfra.exitMasterBlockchainProcess(process)
            heartbeatManager.removeListener(chain.chainId)
            container.terminateProcess(chain.chainId)
            blockchainProcessesDiagnosticData.remove(chain.brid)
            chainIdToBrid.remove(chain.chainId)
            process to containerInitializer.removeContainerChainDir(fs, chain) // TODO: [POS-164]: Redesign
        } else {
            null to false
        }
    }

    private fun terminateBlockchainProcessRpc(chainId: Long, psContainer: PostchainContainer): ContainerBlockchainProcess? {
        return psContainer.terminateProcess(chainId)
                ?.also { process ->
                    masterBlockchainInfra.exitMasterBlockchainProcess(process)
                    heartbeatManager.removeListener(chainId)
                    blockchainProcessesDiagnosticData.remove(chainIdToBrid.remove(chainId))
                    process.shutdown()
                }
    }

    private fun clearBlockchainProcessResources(chain: Chain): Boolean {
        return containerInitializer.removeContainerChainDir(fs, chain)
    }

    private fun findDockerContainer(containerName: ContainerName): Container? {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        return all.firstOrNull { it.hasName(containerName.name) }
    }

    override fun getLaunchedBlockchains(): Set<Long> {
        // FYI: chain0 + chainsOf(starting|running containers)
        return super.getLaunchedBlockchains() + getStartingOrRunningContainerBlockchains()
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

    private fun stopRunningContainersIfExist() {
        if (containerNodeConfig.healthcheckRunningContainersAtStartRegexp.isNotEmpty()) {
            val toStop = dockerClient.listContainers().filter {
                try {
                    containerName(it).contains(Regex(containerNodeConfig.healthcheckRunningContainersAtStartRegexp))
                } catch (e: Exception) {
                    logger.error { "Regexp expression error: ${containerNodeConfig.healthcheckRunningContainersAtStartRegexp}" }
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
