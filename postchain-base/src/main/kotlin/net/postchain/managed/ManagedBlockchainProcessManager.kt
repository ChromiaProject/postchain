// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.StorageBuilder
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.core.*
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockTrace
import net.postchain.ebft.heartbeat.*
import net.postchain.gtx.GTXModule

/**
 * Extends on the [BaseBlockchainProcessManager] with managed mode. "Managed" means that the nodes automatically
 * share information about configuration changes, where "manual" means manual configuration on each node.
 *
 * Background
 * ----------
 * When the configuration of a blockchain has changed, there is a block height specified from when the new
 * BC config should be used, and before a block of this height is built the chain should be restarted so the
 * new config settings can be applied (this is the way "manual" mode works too, so nothing new about that).
 *
 * New
 * ----
 * What is unique with "managed mode" is that a blockchain is used for storing the configurations of the other chains.
 * This "config blockchain" is called "chain zero" (because it has chainIid == 0). By updating the configuration in
 * chain zero, the changes will spread to all nodes the normal way (via EBFT). We still have to restart a chain
 * every time somebody updates its config.
 *
 * Most of the logic in this class is about the case when we need to check chain zero itself, and the most serious
 * case is when the peer list of the chain zero has changed (in this case restarting chains will not be enough).
 *
 * Sync of restart
 * ---------------
 * A great deal of work in this class has to do with the [AfterCommitHandler], which is usually called after a block
 * has been build to see if we need to upgrade anything about the chain's configuration.
 * Since ProcMan doesn't like to do many important things at once, we block (=synchorize) in the beginning of
 * "wrappedRestartHandler()", and only let go after we are done. If there are errors somewhere else in the code,
 * we will see threads deadlock waiting for the lock in wrappedRestartHandler() (see test [ForkTestNightly]
 * "testAliasesManyLevels()" for an example that (used to cause) deadlock).
 *
 * Doc: see the /doc/postchain_ManagedModeFlow.graphml (created with yEd)
 *
 */
@Suppress("PropertyName")
open class ManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : BaseBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider,
        bpmExtensions
) {

    protected open lateinit var directoryDataSource: DirectoryDataSource
    protected var peerListVersion: Long = -1
    protected val CHAIN0 = 0L
    protected val heartbeatConfig = HeartbeatConfig.fromAppConfig(appConfig)
    protected val heartbeatManager = DefaultHeartbeatManager()

    companion object : KLogging()

    /**
     * Check if this is the "chain zero" and if so we need to set the dataSource in a few objects before we go on.
     */
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid {
        if (chainId == CHAIN0) {
            initManagedEnvironment()
        }
        return super.startBlockchain(chainId, bTrace)
    }

    protected open fun initManagedEnvironment() {
        try {
            directoryDataSource = buildChain0ManagedDataSource()
            peerListVersion = directoryDataSource.getPeerListVersion()

            // Setting up managed data source to the nodeConfig
            (postchainContext.nodeConfigProvider as? ManagedNodeConfigurationProvider)
                    ?.setPeerInfoDataSource(directoryDataSource)
                    ?: logger.warn { "Node config is not managed, no peer info updates possible" }

            // Setting up managed data source to the blockchainConfig
            (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                    ?.setDataSource(directoryDataSource)
                    ?: logger.warn { "Blockchain config is not managed" }

        } catch (e: Exception) {
            // TODO: [POS-90]: Improve error handling here
            logger.error { e.message }
        }
    }

    // TODO: [POS-129]: 'protected open' for tests only. Change that.
    protected open fun buildChain0ManagedDataSource(): DirectoryDataSource {
        val storage = StorageBuilder.buildStorage(
                postchainContext.appConfig)

        val blockQueries = withReadWriteConnection(storage, CHAIN0) { ctx0 ->
            val configuration = blockchainConfigProvider.getActiveBlocksConfiguration(ctx0, CHAIN0)
                    ?: throw ProgrammerMistake("chain0 configuration not found")

            val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(
                    configuration, ctx0, NODE_ID_AUTO, CHAIN0, buildModuleInitializer())

            blockchainConfig.makeBlockQueries(storage)
        }

        return createDataSource(blockQueries)
    }

    protected open fun createDataSource(blockQueries: BlockQueries) =
            BaseDirectoryDataSource(blockQueries, postchainContext.appConfig, null)

    override fun awaitPermissionToProcessMessages(blockchainConfig: BlockchainConfiguration): (Long, () -> Boolean) -> Boolean {
        return if (!heartbeatConfig.enabled || blockchainConfig.chainID == 0L) {
            { _, _ -> true }
        } else {
            val hbListener: HeartbeatListener = DefaultHeartbeatListener(heartbeatConfig, blockchainConfig.chainID)
            heartbeatManager.addListener(blockchainConfig.chainID, hbListener);
            awaitHeartbeatHandler(hbListener, heartbeatConfig)
        }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        heartbeatManager.removeListener(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
    }

    /**
     * @return a [AfterCommitHandler] which is a lambda (This lambda will be called by the Engine after each block
     *          has been committed.)
     */
    override fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {

        /**
         * If the chain we are checking is the chain zero itself, we must verify if the list of peers have changed.
         * A: If we have new peers we will need to restart the node (or update the peer connections somehow).
         * B: If not, we just check with chain zero what chains we need and run those.
         *
         * @return "true" if a restart was needed
         */
        fun afterCommitHandlerChain0(bTrace: BlockTrace?, blockTimestamp: Long): Boolean {
            wrTrace("chain0 begin", chainId, bTrace)
            // Sending heartbeat to other chains
            heartbeatManager.beat(blockTimestamp)

            // Preloading blockchain configuration
            preloadChain0Configuration()

            // Checking out the peer list changes
            val doReload = isPeerListChanged()

            return if (doReload) {
                logger.info { "Reloading of blockchains are required" }
                wrTrace("chain0 Reloading of blockchains are required", chainId, bTrace)
                reloadBlockchainsAsync(bTrace)
                true

            } else {
                wrTrace("about to restart chain0", chainId, bTrace)

                // Checking out for chain0 configuration changes
                val reloadChain0 = isConfigurationChanged(CHAIN0)
                startStopBlockchainsAsync(reloadChain0, bTrace)
                reloadChain0
            }
        }

        /**
         * If it's not the chain zero we are looking at, all we need to do is:
         * a) see if configuration has changed and
         * b) restart the chain if this is the case.
         *
         * @param chainId is the chain we should check (cannot be chain zero).
         */
        fun afterCommitHandlerChainN(bTrace: BlockTrace?): Boolean {
            // Checking out for a chain configuration changes
            wrTrace("chainN, begin", chainId, bTrace)

            return if (isConfigurationChanged(chainId)) {
                wrTrace("chainN, restart needed", chainId, bTrace)
                startBlockchainAsync(chainId, bTrace)
                true
            } else {
                wrTrace("chainN, no restart", chainId, bTrace)
                false
            }
        }

        /**
         * Wrapping the [AfterCommitHandler] in a try-catch.
         */
        fun wrappedAfterCommitHandler(bTrace: BlockTrace?, blockHeight: Long, blockTimestamp: Long): Boolean {
            return try {
                wrTrace("Before", chainId, bTrace)
                for (e in extensions) e.afterCommit(blockchainProcesses[chainId]!!, blockHeight)

                wrTrace("Sync", chainId, bTrace)
                // If chain is already being stopped/restarted by another thread we will not get the lock and may return
                if (!tryAcquireChainLock(chainId)) return false

                val restart = if (chainId == CHAIN0) {
                    afterCommitHandlerChain0(bTrace, blockTimestamp)
                } else {
                    afterCommitHandlerChainN(bTrace)
                }

                wrTrace("After", chainId, bTrace)
                restart
            } catch (e: Exception) {
                logger.error("Exception in restart handler: $e")
                e.printStackTrace()
                startBlockchainAsync(chainId, bTrace)
                true // let's hope restarting a blockchain fixes the problem
            } finally {
                releaseChainLock(chainId)
            }
        }

        return ::wrappedAfterCommitHandler
    }

    /**
     * Restart all chains. Begin with chain zero.
     */
    private fun reloadBlockchainsAsync(bTrace: BlockTrace?) {
        synchronized(synchronizer) {
            reloadAllDebug("Begin", bTrace)
            val toLaunch = retrieveBlockchainsToLaunch()
            val launched = getLaunchedBlockchains()
            logChains(toLaunch, launched, true)

            // Starting blockchains: at first chain0, then the rest
            reloadAllInfo("Launching blockchain", 0)
            startBlockchainAsync(0L, bTrace)

            // Launching new blockchains except blockchain 0
            toLaunch.filter { it != 0L }
                    .forEach {
                        reloadAllInfo("Launching blockchain", it)
                        startBlockchainAsync(it, bTrace)
                    }

            // Stopping launched blockchains
            launched.filterNot(toLaunch::contains)
                    .filter { it in launched }
                    .forEach {
                        reloadAllInfo("Stopping blockchain", it)
                        stopBlockchainAsync(it, bTrace)
                    }
        }
    }

    /**
     * Only the chains in the [toLaunch] list should run. Any old chains not in this list must be stopped.
     * Note: any chains not in the new config for this node should actually also be deleted, but not impl yet.
     *
     * @param toLaunch the chains to run
     * @param launched is the old chains. Maybe stop some of them.
     * @param reloadChain0 is true if the chain zero must be restarted.
     */
    private fun startStopBlockchainsAsync(reloadChain0: Boolean, bTrace: BlockTrace?) {
        synchronized(synchronizer) {
            ssaTrace("Begin", bTrace)
            val toLaunch = retrieveBlockchainsToLaunch()
            val launched = getLaunchedBlockchains()
            logChains(toLaunch, launched, reloadChain0)

            // Launching blockchain 0
            if (reloadChain0) {
                ssaInfo("Reloading of blockchain 0 is required, launching it", 0L)
                startBlockchainAsync(0L, bTrace)
            }

            // Launching new blockchains except blockchain 0
            toLaunch.filter { it != 0L }
                    .filter { it !in launched }
                    .forEach {
                        ssaInfo("Launching blockchain", it)
                        startBlockchainAsync(it, bTrace)
                    }

            // Stopping launched blockchains
            launched.filterNot(toLaunch::contains)
                    .filter { it in launched }
                    .forEach {
                        ssaInfo("Stopping blockchain", it)
                        stopBlockchainAsync(it, bTrace)
                    }
            ssaTrace("End", bTrace)
        }
    }

    override fun buildModuleInitializer(): (GTXModule) -> Unit {
        return {
            if (it is DirectoryComponent) {
                it.setDirectoryDataSource(directoryDataSource)
            }
        }
    }

    private fun logChains(toLaunch: Set<Long>, launched: Set<Long>, reloadChain0: Boolean = false) {
        if (/*logger.isInfoEnabled*/ logger.isDebugEnabled) {
            val toLaunch0 = if (reloadChain0 && CHAIN0 !in toLaunch) toLaunch.plus(0L) else toLaunch

            logger./*info*/ debug {
                val pubKey = postchainContext.appConfig.pubKey
                val peerInfos = postchainContext.nodeConfigProvider.getConfiguration().peerInfoMap
                "pubKey: $pubKey" +
                        ", peerInfos: ${peerInfos.keys.toTypedArray().contentToString()}" +
                        ", chains to launch: ${toLaunch0.toTypedArray().contentDeepToString()}" +
                        ", chains launched: ${launched.toTypedArray().contentDeepToString()}"
            }
        }
    }

    /**
     * Makes sure the next configuration is stored in DB.
     *
     * @param chainId is the chain we are interested in.
     */
    protected fun preloadChain0Configuration() {
        withWriteConnection(storage, CHAIN0) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val brid = db.getBlockchainRid(ctx)!! // We can only load chains this way if we know their BC RID.
            val height = db.getLastBlockHeight(ctx)
            val nextConfigHeight = directoryDataSource.findNextConfigurationHeight(brid.data, height)
            if (nextConfigHeight != null) {
                logger.info { "Next config height found in managed-mode module: $nextConfigHeight" }
                if (BaseConfigurationDataStore.findConfigurationHeightForBlock(ctx, nextConfigHeight) != nextConfigHeight) {
                    logger.info {
                        "Configuration for the height $nextConfigHeight is not found in ConfigurationDataStore " +
                                "and will be loaded into it from managed-mode module"
                    }
                    val config = directoryDataSource.getConfiguration(brid.data, nextConfigHeight)!!
                    BaseConfigurationDataStore.addConfigurationData(ctx, nextConfigHeight, config)
                }
            }

            true
        }
    }

    /**
     * Will call chain zero to ask what chains to run.
     *
     * Note: We use [computeBlockchainList()] which is the API method "nm_compute_blockchain_list" of this node's own
     * API for chain zero.
     *
     * @return all chainIids chain zero thinks we should run.
     */
    protected open fun retrieveBlockchainsToLaunch(): Set<Long> {
        retrieveTrace("Begin")
        // chain-zero is always in the list
        val blockchains = mutableSetOf(CHAIN0)

        withWriteConnection(storage, 0) { ctx0 ->
            val db = DatabaseAccess.of(ctx0)

            val locallyConfiguredReplicas = postchainContext.nodeConfigProvider.getConfiguration().blockchainsToReplicate
            val domainBlockchainSet = directoryDataSource.computeBlockchainList().map { BlockchainRid(it) }.toSet()
            val allMyBlockchains = domainBlockchainSet.union(locallyConfiguredReplicas)
            allMyBlockchains.map { blockchainRid ->
                val chainId = db.getChainId(ctx0, blockchainRid)
                retrieveTrace("launch chainIid: $chainId,  BC RID: ${blockchainRid.toShortHex()} ")
                if (chainId == null) {
                    val newChainId = maxOf(db.getMaxChainId(ctx0) ?: 0, 99) + 1
                    withReadWriteConnection(storage, newChainId) { newCtx ->
                        db.initializeBlockchain(newCtx, blockchainRid)
                    }
                    newChainId
                } else {
                    chainId
                }
            }.filter { it != CHAIN0 }.forEach {
                blockchains.add(it)
            }
            true
        }
        retrieveTrace("End, restart: ${blockchains.size}.")
        return blockchains.toSet()
    }

    protected open fun getLaunchedBlockchains(): Set<Long> {
        return blockchainProcesses.keys
    }

    protected fun isPeerListChanged(): Boolean {
        val prev = peerListVersion
        peerListVersion = directoryDataSource.getPeerListVersion()
        return prev != peerListVersion
    }

    // ----------------------------------------------
    // To cut down on boilerplate logging in code
    // ----------------------------------------------
    // Start Stop Async BC
    private fun ssaTrace(str: String, bTrace: BlockTrace?) {
        if (logger.isTraceEnabled) {
            logger.trace("[${nodeName()}]: startStopBlockchainsAsync() -- $str: block causing the start-n-stop async: $bTrace")
        }
    }

    private fun ssaInfo(str: String, chainId: Long) {
        if (logger.isInfoEnabled) {
            logger.info("[${nodeName()}]: startStopBlockchainsAsync() - $str: chainId: $chainId")
        }
    }

    //  wrappedRestartHandler()
    protected fun wrTrace(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isTraceEnabled) {
            logger.trace("[${nodeName()}]: wrappedRestartHandler() -- $str: chainId: $chainId, block causing handler to run: $bTrace")
        }
    }

    protected fun rTrace(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isTraceEnabled) {
            logger.trace("[${nodeName()}]: RestartHandler() -- $str: chainId: $chainId, block causing handler to run: $bTrace")
        }
    }

    protected fun rInfo(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isInfoEnabled) {
            logger.info("[${nodeName()}]: RestartHandler() -- $str: chainId: $chainId, block causing handler to run: $bTrace")
        }
    }

    // reloadBlockchainsAsync()
    private fun reloadAllDebug(str: String, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            logger.debug("[${nodeName()}]: reloadBlockchainsAsync() -- $str: block causing full reload: $bTrace")
        }
    }

    private fun reloadAllInfo(str: String, chainId: Long) {
        if (logger.isInfoEnabled) {
            logger.info("${nodeName()}: reloadBlockchainsAsync() -- $str: chainId: $chainId")
        }
    }

    // retrieveBlockchainsToLaunch()()
    protected fun retrieveTrace(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("retrieveBlockchainsToLaunch() -- $str ")
        }
    }

    protected fun retrieveDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("retrieveBlockchainsToLaunch() -- $str ")
        }
    }
}