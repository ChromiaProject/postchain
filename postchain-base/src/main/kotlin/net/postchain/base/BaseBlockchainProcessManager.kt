// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.icmf.IcmfController
import net.postchain.base.icmf.IcmfPipeConnectionSync
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.QuickSimpleConfigReader
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.devtools.PeerNameHelper.peerName
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Will run many chains as [BlockchainProcess]:es and keep them in a map.
 *
 * Synchronization
 * ---------------
 * As described in the subclass [ManagedBlockchainProcessManager], this class will block (=synchronize) when
 * taking action, which means every other thread demanding a start/stop will have to wait.
 * If you don't want to wait for startup you can schedule a start via a the "startBlockchainAsync()"
 */
open class BaseBlockchainProcessManager(
        protected val blockchainInfrastructure: BlockchainInfrastructure,
        protected val nodeConfigProvider: NodeConfigurationProvider,
        protected val blockchainConfigProvider: BlockchainConfigurationProvider,
        protected val nodeDiagnosticContext: NodeDiagnosticContext
) : BlockchainProcessManager {

    override val synchronizer = Any()

    val nodeConfig = nodeConfigProvider.getConfiguration()
    val storage = StorageBuilder.buildStorage(nodeConfig.appConfig, NODE_ID_TODO)
    protected val blockchainProcesses = mutableMapOf<Long, BlockchainProcess>()

    // FYI: [et]: For integration testing. Will be removed or refactored later
    private val blockchainProcessesLoggers = mutableMapOf<Long, Timer>() // TODO: [POS-90]: ?
    protected val executor: ExecutorService = Executors.newSingleThreadScheduledExecutor()

    // We need to use the same [IcmfController] for all chains or else they won't "see" each other (and be unable to connect).
    protected val icmfController = IcmfController()

    // For DEBUG only
    var insideATest = false
    var blockDebug: BlockTrace? = null

    companion object : KLogging()

    /**
     * Put the startup operation of chainId in the [executor]'s work queue.
     *
     * @param chainId is the chain to start.
     */
    protected fun startBlockchainAsync(chainId: Long, bTrace: BlockTrace?) {
        startAsyncInfo("Enqueue async starting of blockchain", chainId)
        executor.execute {
            try {
                startBlockchain(chainId, bTrace)
            } catch (e: Exception) {
                logger.error(e) { e.message }
            }
        }
    }

    /**
     * Will stop the chain and then start it as a [BlockchainProcess].
     *
     * @param chainId is the chain to start
     * @param bTrace is the block that CAUSED this restart (needed for serious program flow tracking)
     * @return the Blockchain's RID if successful, else null
     */
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid? {
        return synchronized(synchronizer) {
            try {
                startDebug("Begin by stopping blockchain", chainId, bTrace)
                stopBlockchain(chainId, bTrace, true)

                startInfo("Starting of blockchain", chainId)
                withReadWriteConnection(storage, chainId) { eContext ->
                    val configuration = blockchainConfigProvider.getActiveBlocksConfiguration(eContext, chainId)
                    if (configuration != null) {

                        val componentMap = buildComponentMap()
                        val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(
                                configuration, eContext, NODE_ID_AUTO, chainId, componentMap)

                        val processName = BlockchainProcessName(nodeConfig.pubKey, blockchainConfig.blockchainRid)
                        startDebug("BlockchainConfiguration has been created", processName, chainId, bTrace)

                        val x: AfterCommitHandler = buildAfterCommitHandler(chainId)
                        val engine = blockchainInfrastructure.makeBlockchainEngine( processName, blockchainConfig, x)
                        startDebug("BlockchainEngine has been created", processName, chainId, bTrace)

                        /*
                        Definition: cross-fetching is the process of downloading blocks from another blockchain
                        over the peer-to-peer network. This is used when forking a chain when we don't have
                        the old chain locally and we haven't been able to sync using the new chain rid.

                        Problem: in order to cross-fetch blocks, we'd like to get the old blockchain's
                        configuration (to find nodes to connect to). But that's difficult. We don't always
                        have it, and we might not have the most recent configuration.

                        If we don't have that, we can use the current blockchain's configuration to
                        find nodes to sync from, since at least a quorum of the signers from old chain
                        must also be signers of the new chain.

                        To simplify things, we will always use current blockchain configuration to find
                        nodes to cross-fetch from. We'll also use sync-nodes.
                         */

                        val histConf: HistoricBlockchainContext? =
                                if (blockchainConfig.effectiveBlockchainRID != blockchainConfig.blockchainRid) {
                                    val ancestors = nodeConfig.blockchainAncestors[blockchainConfig.blockchainRid]
                                    HistoricBlockchainContext(blockchainConfig.effectiveBlockchainRID, ancestors
                                            ?: emptyMap())
                                } else null


                        val process = blockchainInfrastructure.makeBlockchainProcess(processName, engine, icmfController, histConf)
                        blockchainProcesses[chainId] = process

                        val db = DatabaseAccess.of(eContext)
                        val height = db.getLastBlockHeight(eContext) // FUTURE WORK: Olle: A bit ugly/slow to get this from db here, we should prob pass it as a param from somewhere

                        // Create the pipes we should feed the dispatcher
                        val pipes = icmfController.maybeConnect(process, height) // We only create pipes first time

                        if (logger.isDebugEnabled) {
                            for (pipe in pipes) {
                                // Not much to do with the created pipes since they already got added to the dispatcher in maybeConnect()
                                logger.debug("Pipe created: ${pipe.pipeId} for process: $processName")
                            }
                        }

                        startInfoDebug("Blockchain has been started", processName, chainId, bTrace)
                        blockchainConfig.blockchainRid

                    } else {
                        logger.error("[${nodeName()}]: Can't start blockchain chainId: $chainId due to configuration is absent")
                        null
                    }

                }

            } catch (e: Exception) {
                logger.error(e) { e.message }
                null
            }
        }
    }

    /**
     * In the future there probably should be some way to transport unique configuration components
     * into the map (initially set in the the configuration file?). Currently it's not needed.
     *
     * @return the component map we will use for the configuration of this BC process.
     */
    private fun buildComponentMap(): MutableMap<String, Any> {
        val cm = HashMap<String, Any>()
        // cm["xxx"] = Xxx()  // <-- This is how we can add stuff
        return cm
    }

    override fun retrieveBlockchain(chainId: Long): BlockchainProcess? {
        return blockchainProcesses[chainId]
    }

    /**
     * Will call "shutdown()" on the [BlockchainProcess] and remove it from the list.
     *
     * @param chainId is the chain to be stopped.
     */
    override fun stopBlockchain(chainId: Long, bTrace: BlockTrace?, restart: Boolean) {
        synchronized(synchronizer) {
            stopInfoDebug("Stopping of Blockchain", chainId, bTrace)

            // We shut off ICMF before the chain stops (since it can be aborted anytime without lost messages)
            icmfController.chainStop(chainId)

            blockchainProcesses.remove(chainId)?.also {
                if (restart) {
                    blockchainInfrastructure.restartBlockchainProcess(it)
                } else {
                    blockchainInfrastructure.exitBlockchainProcess(it)
                }
                it.shutdown()
            }
            stopInfoDebug("Stopping blockchain, shutdown complete", chainId, bTrace)

            blockchainProcessesLoggers.remove(chainId)?.also {
                it.cancel()
                it.purge()
            }
            stopDebug("Blockchain process has been purged", chainId, bTrace)
        }
    }

    override fun shutdown() {
        logger.debug("[${nodeName()}]: Stopping BlockchainProcessManager")
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS)

        blockchainProcesses.forEach {
            blockchainInfrastructure.exitBlockchainProcess(it.value)
            it.value.shutdown()
        }
        blockchainProcesses.clear()

        blockchainProcessesLoggers.forEach { (_, t) ->
            t.cancel()
            t.purge()
        }

        storage.close()
        logger.debug("[${nodeName()}]: Stopped BlockchainProcessManager")
    }

    /**
     * Define what actions should be taken after block commit. In our case:
     * 1) alerts ICMF about new block height,
     * 2) checks for configuration changes, and then does a async reboot of the given chain.
     *
     * @param chainId - the chain we should build the [AfterCommitHandler] for
     * @return a newly created [AfterCommitHandler]. This method will be much more complex is
     * the sublcass [net.postchain.managed.ManagedBlockchainProcessManager].
     */
    protected open fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {
        val retFun: (BlockTrace?, Long) -> Boolean = { bTrace, height ->

            // One option (that we currently don't use), is to trigger ICMF pipes
            // after block commit. Instead we fetch the block headers at the time the
            // listener chain needs it.
            //icmfController.icmfDispatcher.newBlockHeight(chainId, height, storage)

            val doRestart = withReadConnection(storage, chainId) { eContext ->
                blockchainConfigProvider.activeBlockNeedsConfigurationChange(eContext, chainId)
            }

            if (doRestart) {
                testDebug("BaseBlockchainProcessManager, need restart of: $chainId", bTrace)
                startBlockchainAsync(chainId, bTrace)
            }

            doRestart
        }
        return retFun //  (So that we can put a breakpoint here where it won't be mixed with actual execution of the function)
    }

    protected fun nodeName(): String {
        return peerName(nodeConfig.pubKey)
    }

    // FYI: [et]: For integration testing. Will be removed or refactored later
    private fun logPeerTopology(chainId: Long) {
        // TODO: [et]: Fix links to EBFT entities
        val topology = ((blockchainInfrastructure as BaseBlockchainInfrastructure)
                .defaultSynchronizationInfrastructure as? EBFTSynchronizationInfrastructure)
                ?.connectionManager?.getPeersTopology(chainId)
                ?.mapKeys {
                    peerName(it.key)
                }
                ?: emptyMap()

        val prettyTopology = topology.mapValues {
            it.value
                    .replace("c>", "${nodeName()}>")
                    .replace("s<", "${nodeName()}<")
                    .replace("<c", "<${peerName(it.key)}")
                    .replace(">s", ">${peerName(it.key)}")
        }

        logger.trace {
            "[${nodeName()}]: Topology: ${prettyTopology.values}"
        }
    }

    // ----------------------------------------------
    // To cut down on boilerplate logging in code
    // ----------------------------------------------
    protected fun testDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug(str)
        }

    }

    protected fun testDebug(str: String, bTrace: BlockTrace?) {
        testDebug(str)
        if (logger.isTraceEnabled) {
            logger.trace("$str, block causing this: $bTrace")
        }
    }


    // Start BC async
    private fun startAsyncInfo(str: String, chainId: Long) {
        if (logger.isInfoEnabled) {
            logger.info("[${nodeName()}]: startBlockchainAsync() - $str: chainId: $chainId")
        }
    }

    // Start BC
    private fun startDebug(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            val extraStr = if (bTrace != null) {
                ", block causing the start: $bTrace"
            } else {
                ""
            }
            logger.debug("[${nodeName()}]: startBlockchain() -- $str: chainId: $chainId $extraStr")
        }
    }

    private fun startDebug(str: String, processName: BlockchainProcessName, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            val extraStr = if (bTrace != null) {
                ", block causing the start: $bTrace"
            } else {
                ""
            }
            logger.debug("$processName: startBlockchain() -- $str: chainId: $chainId $extraStr")
        }
    }

    private fun startInfo(str: String, chainId: Long) {
        if (logger.isInfoEnabled) {
            logger.info("[${nodeName()}]: startBlockchain() - $str: chainId: $chainId")
        }
    }

    private fun startInfo(str: String, processName: BlockchainProcessName, chainId: Long) {
        if (logger.isInfoEnabled) {
            logger.info("$processName: stopBlockchain() - $str: chainId: $chainId")
        }
    }

    private fun startInfoDebug(str: String, processName: BlockchainProcessName, chainId: Long, bTrace: BlockTrace?) {
        startInfo(str, processName, chainId)
        startDebug(str, processName, chainId, bTrace)
    }

    // Stop BC
    private fun stopDebug(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            logger.debug("[${nodeName()}]: stopBlockchain() -- $str: chainId: $chainId, block causing the start: $bTrace")
        }
    }

    private fun stopInfo(str: String, chainId: Long) {
        if (logger.isInfoEnabled) {
            logger.info("[${nodeName()}]: stopBlockchain() - $str: chainId: $chainId")
        }
    }

    private fun stopInfoDebug(str: String, chainId: Long, bTrace: BlockTrace?) {
        stopInfo(str, chainId)
        stopDebug(str, chainId, bTrace)
    }

}