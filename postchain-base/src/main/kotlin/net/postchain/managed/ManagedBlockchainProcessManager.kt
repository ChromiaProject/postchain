package net.postchain.managed

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.toHex
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler
import kotlin.concurrent.withLock

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
class ManagedBlockchainProcessManager(
        blockchainInfrastructure: BlockchainInfrastructure,
        nodeConfigProvider: NodeConfigurationProvider,
        blockchainConfigProvider: BlockchainConfigurationProvider
) : BaseBlockchainProcessManager(
        blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider
) {

    private lateinit var dataSource: ManagedNodeDataSource
    private var lastPeerListVersion: Long? = null

    companion object : KLogging()

    /**
     * Check if this is the "chain zero" and if so we need to set the dataSource in a few objects before we go on.
     */
    override fun startBlockchain(chainId: Long): BlockchainRid? {
        if (chainId == 0L) {
            dataSource = buildChain0ManagedDataSource()

            logger.info { "${nodeConfigProvider.javaClass}" }

            // Setting up managed data source to the nodeConfig
            (nodeConfigProvider as? ManagedNodeConfigurationProvider)
                    ?.setPeerInfoDataSource(dataSource)
                    ?: logger.warn { "Node config is not managed, no peer info updates possible" }

            logger.info { "${blockchainConfigProvider.javaClass}" }

            // Setting up managed data source to the blockchainConfig
            (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                    ?.setDataSource(dataSource)
                    ?: logger.warn { "Blockchain config is not managed" }
        }

        return super.startBlockchain(chainId)
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
            logger.debug("restartHandlerChain0() - start")
            return synchronizer.withLock {
                // Preloading blockchain configuration
                loadBlockchainConfiguration(0L)

                // Checking out for a peers set changes
                val peerListVersion = dataSource.getPeerListVersion()
                val doReload = (lastPeerListVersion != null) && (lastPeerListVersion != peerListVersion)
                lastPeerListVersion = peerListVersion

                if (doReload) {
                    logger.info { "Reloading of blockchains are required" }
                    reloadBlockchainsAsync()
                    logger.debug("restartHandlerChain0() - end 1")
                    true

                } else {
                    val toLaunch = retrieveBlockchainsToLaunch()
                    val launched = blockchainProcesses.keys

                    // Checking out for a chain0 configuration changes
                    val reloadBlockchainConfig = withReadConnection(storage, 0L) { eContext ->
                        blockchainConfigProvider.needsConfigurationChange(eContext, 0L)
                    }

                    // Launching blockchain 0
                    val reloadChan0 = 0L in toLaunch && (0L !in launched || reloadBlockchainConfig)
                    startStopBlockchainsAsync(toLaunch, launched, reloadChan0)
                    logger.debug("restartHandlerChain0() - end 2")
                    reloadChan0
                }
            }
        }

        /**
         * If it's not the chain zero we are looking at, all we need to do is:
         * a) see if configuration has changed and
         * b) restart the chain if this is the case.
         *
         * @param chainId is the chain we should check (cannot be chain zero).
         */
        fun restartHandler(chainId: Long): Boolean {
            logger.debug("restartHandler() - start")
            return synchronizer.withLock {
                // Preloading blockchain configuration
                if (inManagedMode()) {
                    loadBlockchainConfiguration(chainId)
                }

                // Checking out for a chain configuration changes
                val reloadBlockchainConfig = withReadConnection(storage, chainId) { eContext ->
                    (blockchainConfigProvider.needsConfigurationChange(eContext, chainId))
                }

                if (reloadBlockchainConfig) {
                    reloadBlockchainConfigAsync(chainId)
                    logger.debug("restartHandler() - end")
                    true
                } else {
                    logger.debug("restartHandler() - end")
                    false
                }
            }
        }

        // Note: Here we create a Lambda that will call different functions depending on we are talking about the
        // chainIid == 0 or not. The Lambda is of the type () -> Boolean, which is what [RestartHandler] is.
        return {
            if (chainId == 0L) restartHandlerChain0() else restartHandler(chainId)
        }
    }

    private fun buildChain0ManagedDataSource(): ManagedNodeDataSource {
        val chainId = 0L
        var blockQueries: BlockQueries? = null

        withWriteConnection(storage, chainId) { eContext ->
            val configuration = blockchainConfigProvider.getConfiguration(eContext, chainId)
                    ?: throw ProgrammerMistake("chain0 configuration not found")

            val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(
                    configuration,
                    eContext,
                    NODE_ID_AUTO,
                    chainId)

            blockchainConfig.initializeDB(eContext)

            val storage = StorageBuilder.buildStorage(nodeConfigProvider.getConfiguration().appConfig, NODE_ID_NA)
            blockQueries = blockchainConfig.makeBlockQueries(storage)
            true
        }

        return GTXManagedNodeDataSource(blockQueries!!, nodeConfigProvider.getConfiguration())
    }

    /**
     * Restart all chains. Begin with chain zero.
     */
    private fun reloadBlockchainsAsync() {
        executor.submit {
            val toLaunch = retrieveBlockchainsToLaunch()

            // Reloading
            // FYI: For testing only. It can be deleted later.
            logger.info {
                val pubKey = nodeConfigProvider.getConfiguration().pubKey
                val peerInfos = nodeConfigProvider.getConfiguration().peerInfoMap
                "reloadBlockchainsAsync: " +
                        "pubKey: $pubKey" +
                        ", peerInfos: ${peerInfos.keys.toTypedArray().contentToString()} " +
                        ", chains to launch: ${toLaunch.contentDeepToString()}"
            }

            // Starting blockchains: at first chain0, then the rest
            logger.info { "Launching blockchain 0" }
            startBlockchain(0L)

            toLaunch.filter { it != 0L }.forEach {
                logger.info { "Launching blockchain $it" }
                startBlockchain(it)
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
    private fun startStopBlockchainsAsync(toLaunch: Array<Long>, launched: Set<Long>, reloadChain0: Boolean) {
        logger.debug("startStopBlockchainsAsync() - start")
        executor.submit {
            // Launching blockchain 0
            if (reloadChain0) {
                logger.info { "Reloading of blockchain 0 is required" }
                logger.info { "Launching blockchain 0" }
                startBlockchain(0L)
            }

            if (logger.isDebugEnabled()) {
                val toL = toLaunch.map { it.toString() }.reduce { s1, s2 -> "$s1 , $s2" }
                val l = launched.map { it.toString() }.reduce { s1, s2 -> "$s1 , $s2" }
                logger.debug("Chains to launch: $toL. Chains already launched: $l")
            }

            // Launching new blockchains except blockchain 0
            toLaunch.filter { it != 0L }
                    .filter { retrieveBlockchain(it) == null }
                    .forEach {
                        logger.info { "Launching blockchain $it" }
                        startBlockchain(it)
                    }

            // Stopping launched blockchains
            launched.filterNot(toLaunch::contains)
                    .filter { retrieveBlockchain(it) != null }
                    .forEach {
                        logger.info { "Stopping blockchain $it" }
                        stopBlockchain(it)
                    }
        }
        logger.debug("startStopBlockchainsAsync() - end")
    }

    private fun reloadBlockchainConfigAsync(chainId: Long) {
        executor.submit {
            startBlockchain(chainId)
        }
    }

    /**
     * Makes sure the next configuration is stored in DB.
     *
     * @param chainId is the chain we are interested in.
     */
    private fun loadBlockchainConfiguration(chainId: Long) {

        logger.debug("loadBlockchainConfiguration() - start")
        withWriteConnection(storage, chainId) { ctx ->
            val dbAccess = DatabaseAccess.of(ctx)
            val brid = dbAccess.getBlockchainRID(ctx)!! // We can only load chains this way if we know their BC RID.
            val height = dbAccess.getLastBlockHeight(ctx)
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

            logger.debug("loadBlockchainConfiguration() - end")
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
    private fun retrieveBlockchainsToLaunch(): Array<Long> {
        logger.debug("retrieveBlockchainsToLaunch() - start")
        val blockchains = mutableListOf<Long>()

        withWriteConnection(storage, 0) { ctx0 ->
            val dba = DatabaseAccess.of(ctx0)
            dataSource.computeBlockchainList(ctx0)
                    .map { brid ->
                        val blockchainRid = BlockchainRid(brid)
                        val chainIid = dba.getChainId(ctx0, blockchainRid)
                        logger.debug("Computed bc list: chainIid: $chainIid,  BC RID: ${blockchainRid.toShortHex()}  ")
                        if (chainIid == null) {
                            val newChainId = maxOf(
                                    QueryRunner().query(ctx0.conn, "SELECT MAX(chain_iid) FROM blockchains", ScalarHandler<Long>()) + 1,
                                    100)
                            val newCtx = BaseEContext(ctx0.conn, newChainId, ctx0.nodeID, dba)
                            dba.checkBlockchainRID(newCtx, blockchainRid)
                            newChainId
                        } else {
                            chainIid
                        }
                    }
                    .forEach {
                        blockchains.add(it)
                    }

            true
        }
        logger.debug("retrieveBlockchainsToLaunch() - end")

        return blockchains.toTypedArray()
    }

    // TODO: [POS-90]: Redesign this
    private fun inManagedMode(): Boolean {
        logger.warn("We are using isInitialized as a measure of being in managed mode. Doesn't seem right? ")
        return ::dataSource.isInitialized
    }
}