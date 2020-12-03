// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.NodeDiagnosticContext

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
 * A great deal of work in this class has to do with the [RestartHandler], which is usually called after a block
 * has been build to see if we need to upgrade anything about the chain's configuration.
 *
 * Most of the logic in this class is about the case when we need to check chain zero itself, and the most serious
 * case is when the peer list of the chain zero has changed (in this case restarting chains will not be enough).
 *
 * Doc: see the /doc/postchain_ManagedModeFlow.graphml (created with yEd)
 *
 */
open class ManagedBlockchainProcessManager(
        blockchainInfrastructure: BlockchainInfrastructure,
        nodeConfigProvider: NodeConfigurationProvider,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseBlockchainProcessManager(
        blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext
) {

    protected lateinit var dataSource: ManagedNodeDataSource
    private var lastPeerListVersion: Long? = null
    protected val CHAIN0 = 0L

    companion object : KLogging()

    /**
     * Check if this is the "chain zero" and if so we need to set the dataSource in a few objects before we go on.
     */
    override fun startBlockchain(chainId: Long): BlockchainRid? {
        if (chainId == CHAIN0) {
            initManagedEnvironment()
        }
        return super.startBlockchain(chainId)
    }

    private fun initManagedEnvironment() {
        try {
            dataSource = buildChain0ManagedDataSource()

            // TODO: [POS-97]: Put this to DiagnosticContext
//                logger.debug { "${nodeConfigProvider.javaClass}" }

            // Setting up managed data source to the nodeConfig
            (nodeConfigProvider as? ManagedNodeConfigurationProvider)
                    ?.setPeerInfoDataSource(dataSource)
                    ?: logger.warn { "Node config is not managed, no peer info updates possible" }

            // TODO: [POS-97]: Put this to DiagnosticContext
//                logger.debug { "${blockchainConfigProvider.javaClass}" }

            // Setting up managed data source to the blockchainConfig
            (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                    ?.setDataSource(dataSource)
                    ?: logger.warn { "Blockchain config is not managed" }

        } catch (e: Exception) {
            // TODO: [POS-90]: Improve error handling here
            logger.error { e.message }
        }
    }

    private fun buildChain0ManagedDataSource(): ManagedNodeDataSource {
        val storage = StorageBuilder.buildStorage(
                nodeConfigProvider.getConfiguration().appConfig, NODE_ID_NA)

        // Building blockQueries of Rell module for ManagedDataSource
        val blockQueries = withReadWriteConnection(storage, CHAIN0) { ctx0 ->
            val configuration = blockchainConfigProvider.getConfiguration(ctx0, CHAIN0)
                    ?: throw ProgrammerMistake("chain0 configuration not found")

            val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(
                    configuration, ctx0, NODE_ID_AUTO, CHAIN0)

            blockchainConfig.makeBlockQueries(storage)
        }

        return GTXManagedNodeDataSource(blockQueries, nodeConfigProvider.getConfiguration())
    }

    /**
     * @return a [RestartHandler] which is a lambda (This lambda will be called by the Engine after each block
     *          has been committed.)
     */
    override fun restartHandler(chainId: Long): RestartHandler {

        /**
         * If the chain we are checking is the chain zero itself, we must verify if the list of peers have changed.
         * A: If we have new peers we will need to restart the node (or update the peer connections somehow).
         * B: If not, we just check with chain zero what chains we need and run those.
         */
        fun restartHandlerChain0(): Boolean {

            // Preloading blockchain configuration
            preloadChain0Configuration()

            // Checking out for a peers set changes
            val peerListVersion = dataSource.getPeerListVersion()
            val peersChanged = (lastPeerListVersion != null) && (lastPeerListVersion != peerListVersion)
            lastPeerListVersion = peerListVersion

            return if (peersChanged) {
                logger.info { "Reloading of blockchains are required" }
                restartBlockchains(restartChain0 = true, restartChainN = true)
                true

            } else {
                // Checking out for a chain0 configuration changes
                val config0Changed = withReadConnection(storage, CHAIN0) { eContext ->
                    blockchainConfigProvider.needsConfigurationChange(eContext, CHAIN0)
                }
                restartBlockchains(restartChain0 = config0Changed, restartChainN = false)
                config0Changed
            }
        }

        /**
         * If it's not the chain zero we are looking at, all we need to do is:
         * a) see if configuration has changed and
         * b) restart the chain if this is the case.
         *
         * @param chainId is the chain we should check (cannot be chain zero).
         */
        fun restartHandlerChainN(): Boolean {
            // Checking out for a chain configuration changes
            val reloadConfig = withReadConnection(storage, chainId) { eContext ->
                (blockchainConfigProvider.needsConfigurationChange(eContext, chainId))
            }

            return if (reloadConfig) {
                startBlockchainAsync(chainId)
                true
            } else {
                false
            }
        }

        fun wrappedRestartHandler(): Boolean {
            return try {
                synchronized(synchronizer) {
                    if (chainId == CHAIN0) restartHandlerChain0() else restartHandlerChainN()
                }
            } catch (e: Exception) {
                logger.error("Exception in restart handler: $e")
                e.printStackTrace()
                startBlockchainAsync(chainId)
                true // let's hope restarting a blockchain fixes the problem
            }
        }

        return ::wrappedRestartHandler
    }

    /**
     * TODO: [POS-129] Clarify this doc
     * Stops launched blockchains that shouldn't be launched.
     * Restarts launched blockchains that should be restarted (see [restartChainN]).
     * Starts blockchains that should be launched but are stopped.
     * Process a state of chain0 according to value of [restartChain0]
     */
    private fun restartBlockchains(restartChain0: Boolean, restartChainN: Boolean) {
        val launched = getLaunchedBlockchains().minus(CHAIN0)
        val shouldBeLaunched = getBlockchainsShouldBeLaunched().minus(CHAIN0)
        val toStart = shouldBeLaunched - launched
        val toStop = launched - shouldBeLaunched
        val toRestart = if (restartChainN) launched intersect shouldBeLaunched else emptySet()
        logChains(restartChain0, toStart, toStop, toRestart)

        if (restartChain0) {
            startBlockchainAsync(CHAIN0)
        }

        toStop.forEach(::stopBlockchainAsync)
        toRestart.forEach(::startBlockchainAsync)
        toStart.forEach(::startBlockchainAsync)
    }

    private fun logChains(restartChain0: Boolean, toStart: Set<Long>, toStop: Set<Long>, toRestart: Set<Long>) {
        if (logger.isInfoEnabled /*isDebugEnabled*/) {
            val toStart0 = if (restartChain0 && CHAIN0 !in toStart) toStart.plus(CHAIN0) else toStart

            logger.info /*debug*/ {
                val pubKey = nodeConfigProvider.getConfiguration().pubKey
                val peerInfos = nodeConfigProvider.getConfiguration().peerInfoMap
                "pubKey: $pubKey" +
                        ", peerInfos: ${peerInfos.keys.toTypedArray().contentToString()}" +
                        ", chains to start: [${toStart0.joinToString()}]" +
                        ", chains to stop: [${toStop.joinToString()}]" +
                        ", chains to restart: [${toRestart.joinToString()}]"
            }
        }
    }

    /**
     * Makes sure the next configuration is stored in DB.
     *
     * @param chainId is the chain we are interested in.
     */
    private fun preloadChain0Configuration() {
        withWriteConnection(storage, CHAIN0) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val brid = db.getBlockchainRid(ctx)!! // We can only load chains this way if we know their BC RID.
            val height = db.getLastBlockHeight(ctx)
            val nextConfigHeight = dataSource.findNextConfigurationHeight(brid.data, height)
            if (nextConfigHeight != null) {
                logger.info { "Next config height found in managed-mode module: $nextConfigHeight" }
                if (BaseConfigurationDataStore.findConfigurationHeightForBlock(ctx, nextConfigHeight) != nextConfigHeight) {
                    logger.info {
                        "Configuration for the height $nextConfigHeight is not found in ConfigurationDataStore " +
                                "and will be loaded into it from managed-mode module"
                    }
                    val config = dataSource.getConfiguration(brid.data, nextConfigHeight)!!
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
    private fun getBlockchainsShouldBeLaunched(): Set<Long> {
        // chain-zero is always in the list
        val blockchains = mutableSetOf(CHAIN0)

        withWriteConnection(storage, 0) { ctx0 ->
            val db = DatabaseAccess.of(ctx0)
            dataSource.computeBlockchainList()
                    .map { brid ->
                        val blockchainRid = BlockchainRid(brid)
                        val chainId = db.getChainId(ctx0, blockchainRid)
                        logger.debug("Blockchain to launch: chainIid: $chainId,  BC RID: ${blockchainRid.toShortHex()} ")
                        if (chainId == null) {
                            val newChainId = db.getMaxChainId(ctx0)
                                    ?.let { maxOf(it + 1, 100) }
                                    ?: 100
                            withReadWriteConnection(storage, newChainId) { newCtx ->
                                db.initializeBlockchain(newCtx, blockchainRid)
                            }
                            newChainId
                        } else {
                            chainId
                        }
                    }
                    .filter { it != CHAIN0 }
                    .forEach {
                        blockchains.add(it)
                    }
            true
        }

        return blockchains
    }

    protected open fun getLaunchedBlockchains(): Set<Long> {
        return blockchainProcesses.keys
    }

}