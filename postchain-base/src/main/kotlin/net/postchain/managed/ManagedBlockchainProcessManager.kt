// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfig
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.core.*
import net.postchain.core.block.BlockTrace
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModuleAware
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import net.postchain.managed.config.ManagedDataSourceAware
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.withLock
import kotlin.system.measureTimeMillis

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
 * we will see threads deadlock waiting for the lock in wrappedRestartHandler() (see test [ForkSlowIntegrationTest]
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

    protected open lateinit var dataSource: ManagedNodeDataSource
    protected val CHAIN0 = 0L
    protected val areBlockchainsPruning = AtomicBoolean(false)
    protected var currentRemovedBlockchainHeight = 0L

    companion object : KLogging()

    protected open fun initManagedEnvironment(dataSource: ManagedNodeDataSource) {
        this.dataSource = dataSource

        // Setting up managed data source to the nodeConfig
        (postchainContext.nodeConfigProvider as? ManagedNodeConfigurationProvider)
                ?.setPeerInfoDataSource(dataSource)
                ?: logger.warn { "Node config is not managed, no peer info updates possible" }

        // Setting up managed data source to the blockchainConfig
        (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                ?.setManagedDataSource(dataSource)
                ?: logger.warn { "Blockchain config is not managed" }
    }

    override fun afterMakeConfiguration(chainId: Long, blockchainConfig: BlockchainConfiguration) {
        if (chainId == CHAIN0 && blockchainConfig is ManagedDataSourceAware) {
            initManagedEnvironment(blockchainConfig.dataSource)
        }
    }

    override fun getBlockchainConfigurationFactory(chainId: Long): BlockchainConfigurationFactorySupplier =
            BlockchainConfigurationFactorySupplier { factoryName: String ->
                try {
                    val factory = newInstanceOf<GTXBlockchainConfigurationFactory>(factoryName)
                    if (chainId == CHAIN0) {
                        Chain0BlockchainConfigurationFactory(factory, appConfig, blockBuilderStorage)
                    } else {
                        DappBlockchainConfigurationFactory(factory, dataSource)
                    }
                } catch (e: Exception) {
                    throw UserMistake(
                            "[${nodeName()}]: Can't start blockchain chainId: $chainId " +
                                    "due to configuration is wrong. Check /configurationfactory value: $factoryName. " +
                                    "Use ${GTXBlockchainConfigurationFactory::class.qualifiedName} (or subclass)", e
                    )
                }
            }

    /**
     * Here we perform any extra database operations that we want to do related to the block before we finally commit it.
     */
    override fun buildBeforeCommitHandler(blockchainConfig: BlockchainConfiguration): BeforeCommitHandler {
        fun beforeCommitHandlerChain0(bTrace: BlockTrace?, bctx: BlockEContext) {
            logger.trace { "Running before commit handler for chain0 -- block causing handler to run: $bTrace" }
            // Preloading blockchain configuration
            preloadChain0Configuration(bctx, blockchainConfig)
        }

        fun beforeCommitHandlerChainN(bTrace: BlockTrace?, bctx: BlockEContext) {
            logger.trace { "Running before commit handler -- block causing handler to run: $bTrace" }
            saveConfigurationInDatabaseIfNotAlreadyExists(bctx, blockchainConfig)
        }

        fun wrappedBeforeCommitHandler(bTrace: BlockTrace?, bctx: BlockEContext) {
            if (bctx.chainID == CHAIN0) {
                beforeCommitHandlerChain0(bTrace, bctx)
            } else {
                beforeCommitHandlerChainN(bTrace, bctx)
            }
        }

        return ::wrappedBeforeCommitHandler
    }

    /**
     * @return a [AfterCommitHandler] which is a lambda (This lambda will be called by the Engine after each block
     *          has been committed.)
     */
    override fun buildAfterCommitHandler(chainId: Long, blockchainConfig: BlockchainConfiguration): AfterCommitHandler {

        /**
         * If the chain we are checking is the chain zero itself, we must verify if the list of peers have changed.
         * A: If we have new peers we will need to restart the node (or update the peer connections somehow).
         * B: If not, we just check with chain zero what chains we need and run those.
         *
         * @return "true" if a restart was needed
         */
        @Suppress("UNUSED_PARAMETER")
        fun afterCommitHandlerChain0(bTrace: BlockTrace?, blockTimestamp: Long): Boolean {
            wrTrace("chain0 begin", bTrace)
            // Checking out for chain0 configuration changes
            val reloadChain0 = isConfigurationChanged(CHAIN0)
            startStopBlockchainsAsync(reloadChain0, bTrace)
            // Pruning removed blockchains if exist
            pruneRemovedBlockchains()
            return reloadChain0
        }

        /**
         * If it's not the chain zero we are looking at, all we need to do is:
         * a) see if configuration has changed and
         * b) restart the chain if this is the case.
         */
        fun afterCommitHandlerChainN(bTrace: BlockTrace?): Boolean {
            // Checking out for a chain configuration changes
            wrTrace("chainN, begin", bTrace)

            return if (isConfigurationChanged(chainId)) {
                wrTrace("chainN, restart needed", bTrace)
                startBlockchainAsync(chainId, bTrace, blockchainProcesses[chainId]?.isSigner() ?: false)
                true
            } else {
                wrTrace("chainN, no restart", bTrace)
                false
            }
        }

        /**
         * Wrapping the [AfterCommitHandler] in a try-catch.
         */
        fun wrappedAfterCommitHandler(bTrace: BlockTrace?, blockHeight: Long, blockTimestamp: Long): Boolean {
            return try {
                wrTrace("Before", bTrace)

                wrTrace("Sync", bTrace)
                // If chain is already being stopped/restarted by another thread we will not get the lock and may return
                if (!tryAcquireChainLock(chainId)) return false

                invokeAfterCommitHooks(chainId, blockHeight)

                val restart = if (chainId == CHAIN0) {
                    afterCommitHandlerChain0(bTrace, blockTimestamp)
                } else {
                    afterCommitHandlerChainN(bTrace)
                }

                wrTrace("After", bTrace)
                restart
            } catch (e: Exception) {
                logger.error(e) { "Exception in restart handler: $e" }
                startBlockchainAsync(chainId, bTrace, blockchainProcesses[chainId]?.isSigner() ?: false)
                true // let's hope restarting a blockchain fixes the problem
            } finally {
                releaseChainLock(chainId)
            }
        }

        return ::wrappedAfterCommitHandler
    }

    private fun saveConfigurationInDatabaseIfNotAlreadyExists(bctx: BlockEContext, blockchainConfig: BlockchainConfiguration) {
        val db = DatabaseAccess.of(bctx)
        if (db.getConfigurationData(bctx, blockchainConfig.configHash) == null) {
            db.addConfigurationData(bctx, bctx.height, encodeGtv(blockchainConfig.rawConfig))
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
        processLock.withLock {
            ssaTrace("Begin", bTrace)
            val toLaunch = retrieveBlockchainsToLaunch()
            val chainIdsToLaunch = toLaunch.map { it.chainId }.toSet()
            val launched = getLaunchedBlockchains()
            logChains(chainIdsToLaunch, launched.keys, reloadChain0)

            // Launching blockchain 0
            if (reloadChain0) {
                ssaInfo("Reloading of blockchain 0 is required, launching it")
                startBlockchainAsync(0L, bTrace)
            }

            toLaunch.filter { it.chainId != 0L }
                    .forEach {
                        val process = launched[it.chainId]
                        if (process == null) {
                            ssaInfo("Launching blockchain")
                            startBlockchainAsync(it.chainId, bTrace)
                        } else if (process.getBlockchainState() != it.state) {
                            ssaInfo("Restarting blockchain due to state change")
                            startBlockchainAsync(it.chainId, bTrace)
                        }
                    }

            // Stopping launched blockchains
            launched.keys.filterNot(chainIdsToLaunch::contains)
                    .forEach {
                        ssaInfo("Stopping blockchain")
                        stopBlockchainAsync(it, bTrace)
                    }
            ssaTrace("End", bTrace)
        }
    }

    private fun pruneRemovedBlockchains() {
        if (!areBlockchainsPruning.compareAndSet(false, true)) return

        val removedChains = dataSource.findNextRemovedBlockchains(currentRemovedBlockchainHeight)

        // No removed chains, OR some of the removed chains have not yet been stopped. We'll be back on the next iteration.
        if (removedChains.isEmpty() || removedChains.any { retrieveBlockchain(it.rid) != null }) {
            areBlockchainsPruning.set(false)
            return
        }

        executor.execute {
            try {
                withLoggingContext(CHAIN_IID_TAG to CHAIN0.toString()) {
                    logger.debug { "Removed blockchains at height ${removedChains.first().height}: ${removedChains.joinToString(", ") { it.rid.toHex() }}" }
                }

                removedChains.forEach { chain ->
                    val removedChainId = withReadConnection(sharedStorage, 0) { ctx0 ->
                        DatabaseAccess.of(ctx0).getChainId(ctx0, chain.rid)
                    }

                    withLoggingContext(
                            buildMap {
                                put(BLOCKCHAIN_RID_TAG, chain.rid.toString())
                                if (removedChainId != null) put(CHAIN_IID_TAG, removedChainId.toString())
                            }
                    ) {
                        if (removedChainId != null) {
                            logger.debug { "Deleting blockchain" }
                            val elapsed = measureTimeMillis {
                                withReadWriteConnection(sharedStorage, removedChainId) {
                                    BlockchainApi.deleteBlockchain(it)
                                }
                            }
                            logger.debug { "Blockchain deleted in $elapsed ms" }
                        } else {
                            logger.debug { "Blockchain is already deleted" }
                        }
                    }
                }
                currentRemovedBlockchainHeight = removedChains.first().height
            } catch (e: Exception) {
                logger.error(e) { e.message }
            } finally {
                areBlockchainsPruning.set(false)
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
     * Makes sure the next configuration is stored in DB before committing current block.
     */
    private fun preloadChain0Configuration(bctx: BlockEContext, configuration: BlockchainConfiguration) {
        val brid = configuration.blockchainRid
        val currentBlockDataSource = getDatasourceForCurrentBlock(configuration, bctx)
        val nextConfigHeight = currentBlockDataSource.findNextConfigurationHeight(brid.data, bctx.height)
        if (nextConfigHeight != null) {
            val db = DatabaseAccess.of(bctx)
            logger.info { "Next config height found in managed-mode module: $nextConfigHeight" }
            if (db.findConfigurationHeightForBlock(bctx, nextConfigHeight) != nextConfigHeight) {
                logger.info {
                    "Configuration for the height $nextConfigHeight is not found in ConfigurationDataStore " +
                            "and will be loaded into it from managed-mode module"
                }
                val config = currentBlockDataSource.getConfiguration(brid.data, nextConfigHeight)!!
                GTXBlockchainConfigurationFactory.validateConfiguration(GtvDecoder.decodeGtv(config), brid)
                db.addConfigurationData(bctx, nextConfigHeight, config)
            }
        }
    }

    protected open fun getDatasourceForCurrentBlock(configuration: BlockchainConfiguration, bctx: BlockEContext): ManagedNodeDataSource {
        val currentBlockQueryRunner = { name: String, args: Gtv -> (configuration as GTXModuleAware).module.query(bctx, name, args) }
        return BaseManagedNodeDataSource(currentBlockQueryRunner, postchainContext.appConfig)
    }

    /**
     * Will call chain zero to ask what chains to run.
     *
     * Note: We use [computeBlockchainInfoList()] which is the API method "nm_compute_blockchain_info_list" of this node's own
     * API for chain zero.
     *
     * @return all chainIids chain zero thinks we should run.
     */
    protected open fun retrieveBlockchainsToLaunch(): Set<LocalBlockchainInfo> {
        retrieveTrace("Begin")
        // chain-zero is always in the list
        val blockchains = mutableSetOf(LocalBlockchainInfo(CHAIN0, true, BlockchainState.RUNNING))

        withWriteConnection(blockBuilderStorage, 0) { ctx0 ->
            val db = DatabaseAccess.of(ctx0)
            val domainBlockchains = dataSource.computeBlockchainInfoList().distinctBy { it.rid }
            val all = domainBlockchains.union(locallyConfiguredBlockchainsToReplicate())
            all.forEach { blockchainInfo ->
                val chainId = db.getChainId(ctx0, blockchainInfo.rid)
                retrieveTrace("launch chainIid: $chainId,  BC RID: ${blockchainInfo.rid.toShortHex()} ")
                val localBlockchainInfo = if (chainId == null) {
                    val calculatedChainId = if (blockchainInfo.system) {
                        (db.getMaxSystemChainId(ctx0) ?: 0) + 1
                    } else {
                        maxOf(db.getMaxChainId(ctx0) ?: 0, 99) + 1
                    }
                    withReadWriteConnection(blockBuilderStorage, calculatedChainId) { newCtx ->
                        db.initializeBlockchain(newCtx, blockchainInfo.rid)
                    }
                    LocalBlockchainInfo(calculatedChainId, blockchainInfo.system, blockchainInfo.state)
                } else {
                    LocalBlockchainInfo(chainId, blockchainInfo.system, blockchainInfo.state)
                }

                if (localBlockchainInfo.chainId != CHAIN0 && localBlockchainInfo.state != BlockchainState.IMPORTING) {
                    blockchains.add(localBlockchainInfo)
                }
            }
            true
        }
        retrieveTrace("End, restart: ${blockchains.size}.")
        return blockchains
    }

    protected open fun locallyConfiguredBlockchainsToReplicate() =
            (postchainContext.nodeConfigProvider.getConfiguration() as? ManagedNodeConfig)
                    ?.locallyConfiguredBlockchainsToReplicate?.map { BlockchainInfo(it, false, BlockchainState.RUNNING) }?.toSet()
                    ?: emptySet()

    protected open fun getLaunchedBlockchains(): MutableMap<Long, BlockchainProcess> {
        return blockchainProcesses
    }

    // ----------------------------------------------
    // To cut down on boilerplate logging in code
    // ----------------------------------------------
    // Start Stop Async BC
    private fun ssaTrace(str: String, bTrace: BlockTrace?) {
        logger.trace { "startStopBlockchainsAsync() -- $str: block causing the start-n-stop async: $bTrace" }
    }

    private fun ssaInfo(str: String) {
        logger.info { "startStopBlockchainsAsync() - $str" }
    }

    //  wrappedRestartHandler()
    protected fun wrTrace(str: String, bTrace: BlockTrace?) {
        logger.trace { "wrappedRestartHandler() -- $str: block causing handler to run: $bTrace" }
    }

    protected fun rTrace(str: String, bTrace: BlockTrace?) {
        logger.trace { "RestartHandler() -- $str: block causing handler to run: $bTrace" }
    }

    protected fun rInfo(str: String, bTrace: BlockTrace?) {
        logger.info { "RestartHandler() -- $str: block causing handler to run: $bTrace" }
    }

    // retrieveBlockchainsToLaunch()()
    protected fun retrieveTrace(str: String) {
        logger.trace { "retrieveBlockchainsToLaunch() -- $str " }
    }

    protected fun retrieveDebug(str: String) {
        logger.debug { "retrieveBlockchainsToLaunch() -- $str " }
    }

    override fun getBlockchainState(chainId: Long, blockchainRid: BlockchainRid): BlockchainState =
            if (chainId == CHAIN0) BlockchainState.RUNNING else dataSource.getBlockchainState(blockchainRid)
}
