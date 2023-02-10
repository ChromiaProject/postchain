// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.base.data.DependenciesValidator
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.*
import net.postchain.core.block.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DiagnosticValueMap
import net.postchain.debug.OrderedDiagnosticValueSet
import net.postchain.devtools.NameHelper.peerName
import net.postchain.metrics.BLOCKCHAIN_RID_TAG
import net.postchain.metrics.CHAIN_IID_TAG
import net.postchain.metrics.NODE_PUBKEY_TAG
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        protected val postchainContext: PostchainContext,
        protected val blockchainInfrastructure: BlockchainInfrastructure,
        protected val blockchainConfigProvider: BlockchainConfigurationProvider,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : BlockchainProcessManager {

    override val synchronizer = Any()
    protected val chainSynchronizers = mutableMapOf<Long, ReentrantLock>()

    val appConfig = postchainContext.appConfig
    val connectionManager = postchainContext.connectionManager
    val nodeDiagnosticContext = postchainContext.nodeDiagnosticContext
    val storage get() = postchainContext.storage
    protected val blockchainProcesses = mutableMapOf<Long, BlockchainProcess>()
    protected val chainIdToBrid = mutableMapOf<Long, BlockchainRid>()
    protected val bridToChainId = mutableMapOf<BlockchainRid, Long>()
    protected val blockchainDiagnostics = mutableMapOf<BlockchainRid, DiagnosticValueMap>()
    protected val extensions: List<BlockchainProcessManagerExtension> = bpmExtensions
    protected val executor: ExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val scheduledForStart = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    // For DEBUG only
    var insideATest = false
    var blockDebug: BlockTrace? = null

    companion object : KLogging()

    init {
        initiateChainDiagnosticData()
    }

    /**
     * Put the startup operation of chainId in the [executor]'s work queue.
     *
     * @param chainId is the chain to start.
     */
    protected fun startBlockchainAsync(chainId: Long, bTrace: BlockTrace?) {
        if (!scheduledForStart.add(chainId)) {
            logger.info { "Chain $chainId is already scheduled for start" }
            return
        }
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
     * Put the stop operation of chainId in the [executor]'s work queue.
     *
     * @param chainId is the chain to stop.
     */
    protected fun stopBlockchainAsync(chainId: Long, bTrace: BlockTrace?) {
        startAsyncInfo("Enqueue async stopping of blockchain", chainId)
        executor.execute {
            try {
                stopBlockchain(chainId, bTrace)
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
     * @return the Blockchain's RID if successful
     * @throws UserMistake if failed
     */
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid {
        chainSynchronizers.putIfAbsent(chainId, ReentrantLock())
        chainSynchronizers[chainId]!!.withLock {
            return synchronized(synchronizer) {
                withLoggingContext(
                        NODE_PUBKEY_TAG to appConfig.pubKey,
                        CHAIN_IID_TAG to chainId.toString()
                ) {
                    try {
                        startDebug("Begin by stopping blockchain", chainId, bTrace)
                        stopBlockchain(chainId, bTrace, true)

                        startInfo("Starting of blockchain", chainId)
                        val blockchainConfig = makeBlockchainConfiguration(chainId)

                        withLoggingContext(BLOCKCHAIN_RID_TAG to blockchainConfig.blockchainRid.toHex()) {
                            val processName = BlockchainProcessName(appConfig.pubKey, blockchainConfig.blockchainRid)
                            startDebug("BlockchainConfiguration has been created", processName, chainId, bTrace)

                            val x: AfterCommitHandler = buildAfterCommitHandler(chainId)
                            val engine = blockchainInfrastructure.makeBlockchainEngine(processName, blockchainConfig, x)
                            startDebug("BlockchainEngine has been created", processName, chainId, bTrace)

                            createAndRegisterBlockchainProcess(
                                    chainId,
                                    blockchainConfig,
                                    processName,
                                    engine
                            )
                            logger.debug { "$processName: BlockchainProcess has been launched: chainId: $chainId" }

                            startInfoDebug(
                                    "Blockchain has been started",
                                    processName,
                                    chainId,
                                    blockchainConfig.blockchainRid,
                                    bTrace
                            )
                        }
                        blockchainConfig.blockchainRid
                    } finally {
                        scheduledForStart.remove(chainId)
                    }
                }
            }
        }
    }

    protected open fun makeBlockchainConfiguration(chainId: Long): BlockchainConfiguration {
        return withReadWriteConnection(storage, chainId) { eContext ->
            val configuration = blockchainConfigProvider.getActiveBlocksConfiguration(eContext, chainId)
            if (configuration != null) {
                blockchainInfrastructure.makeBlockchainConfiguration(
                        configuration, eContext, NODE_ID_AUTO, chainId, getBlockchainConfigurationFactory(chainId)
                )
            } else {
                throw UserMistake("[${nodeName()}]: Can't start blockchain chainId: $chainId due to configuration is absent")
            }.also { DependenciesValidator.validateBlockchainRids(eContext, it.blockchainDependencies) }
        }
    }

    protected open fun getBlockchainConfigurationFactory(chainId: Long): BlockchainConfigurationFactorySupplier =
            DefaultBlockchainConfigurationFactory()

    protected open fun createAndRegisterBlockchainProcess(
            chainId: Long,
            blockchainConfig: BlockchainConfiguration,
            processName: BlockchainProcessName,
            engine: BlockchainEngine
    ) {
        blockchainProcesses[chainId] = blockchainInfrastructure.makeBlockchainProcess(processName, engine)
                .also {
                    it.registerDiagnosticData(blockchainDiagnostics.getOrPut(blockchainConfig.blockchainRid) { DiagnosticValueMap(DiagnosticProperty.BLOCKCHAIN) })
                    extensions.forEach { ext -> ext.connectProcess(it) }
                    chainIdToBrid[chainId] = blockchainConfig.blockchainRid
                    bridToChainId[blockchainConfig.blockchainRid] = chainId
                }
    }

    override fun retrieveBlockchain(chainId: Long): BlockchainProcess? {
        return chainSynchronizers[chainId]?.withLock {
            blockchainProcesses[chainId]
        }
    }

    override fun retrieveBlockchain(blockchainRid: BlockchainRid): BlockchainProcess? {
        return bridToChainId[blockchainRid]?.let {
            chainSynchronizers[it]?.withLock {
                blockchainProcesses[it]
            }
        }
    }

    /**
     * Will call "shutdown()" on the [BlockchainProcess] and remove it from the list.
     *
     * @param chainId is the chain to be stopped.
     */
    override fun stopBlockchain(chainId: Long, bTrace: BlockTrace?, restart: Boolean) {
        chainSynchronizers[chainId]?.withLock {
            synchronized(synchronizer) {
                withLoggingContext(
                        NODE_PUBKEY_TAG to appConfig.pubKey,
                        CHAIN_IID_TAG to chainId.toString(),
                        BLOCKCHAIN_RID_TAG to chainIdToBrid[chainId]?.toHex()
                ) {
                    stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
                    stopDebug("Blockchain process has been purged", chainId, bTrace)
                }
            }
        }
        if (!restart) {
            chainIdToBrid.remove(chainId).also { bridToChainId.remove(it) }
            chainSynchronizers.remove(chainId)
        }
    }

    protected open fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        val blockchainRid = chainIdToBrid[chainId]
        blockchainDiagnostics.remove(blockchainRid)
        blockchainProcesses.remove(chainId)?.also {
            stopInfoDebug("Stopping of blockchain", chainId, bTrace)
            extensions.forEach { ext -> ext.disconnectProcess(it) }
            if (restart) {
                blockchainInfrastructure.restartBlockchainProcess(it)
            } else {
                blockchainInfrastructure.exitBlockchainProcess(it)
            }
            it.shutdown()
            stopInfoDebug("Stopping blockchain, shutdown complete", chainId, bTrace)
        }
    }

    override fun shutdown() {
        logger.debug("[${nodeName()}]: Stopping BlockchainProcessManager")
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS)

        blockchainProcesses.values.forEach {
            blockchainInfrastructure.exitBlockchainProcess(it)
            extensions.forEach { ext -> ext.disconnectProcess(it) }
            it.shutdown()
        }
        blockchainProcesses.clear()
        blockchainDiagnostics.clear()
        chainIdToBrid.clear()
        bridToChainId.clear()
        logger.debug("[${nodeName()}]: Stopped BlockchainProcessManager")
    }

    /**
     * Define what actions should be taken after block commit. In our case:
     * 1) trigger bp extensions,
     * 2) checks for configuration changes, and then does a async reboot of the given chain.
     *
     * @param chainId - the chain we should build the [AfterCommitHandler] for
     * @return a newly created [AfterCommitHandler]. This method will be much more complex is
     * the sublcass [net.postchain.managed.ManagedBlockchainProcessManager].
     */
    protected open fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {
        return { bTrace, height, _ ->

            for (e in extensions) e.afterCommit(blockchainProcesses[chainId]!!, height)
            val doRestart = withReadConnection(storage, chainId) { eContext ->
                blockchainConfigProvider.activeBlockNeedsConfigurationChange(eContext, chainId)
            }

            if (doRestart) {
                testDebug("BaseBlockchainProcessManager, need restart of: $chainId", bTrace)
                startBlockchainAsync(chainId, bTrace)
            }

            doRestart
        }
    }

    protected fun nodeName(): String {
        return peerName(appConfig.pubKey)
    }

    // FYI: [et]: For integration testing. Will be removed or refactored later
    private fun logPeerTopology(chainId: Long) {
        val topology = connectionManager.getNodesTopology(chainId)
                .mapKeys {
                    peerName(it.key)
                }

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

    protected fun isConfigurationChanged(chainId: Long): Boolean {
        return withReadConnection(storage, chainId) { eContext ->
            blockchainConfigProvider.activeBlockNeedsConfigurationChange(eContext, chainId)
        }
    }

    protected fun initiateChainDiagnosticData() {
        nodeDiagnosticContext.add(
                DiagnosticProperty.BLOCKCHAIN withLazyValue {
                    connectionManager.getNodesTopology().forEach { (blockchainRid, topology) ->
                        blockchainDiagnostics.computeIfPresent(BlockchainRid.buildFromHex(blockchainRid)) { _, properties ->
                            properties.apply {
                                add(DiagnosticProperty.BLOCKCHAIN_NODE_PEERS withLazyValue  { topology })
                            }
                        }
                    }
                }
        )
    }

    protected fun tryAcquireChainLock(chainId: Long): Boolean {
        return chainSynchronizers[chainId]?.tryLock()
                ?: throw ProgrammerMistake("No lock instance exists for chain $chainId")
    }

    protected fun releaseChainLock(chainId: Long) {
        chainSynchronizers[chainId]?.apply {
            if (isHeldByCurrentThread) {
                unlock()
            }
        } ?: throw ProgrammerMistake("No lock instance exists for chain $chainId")
    }

    // ----------------------------------------------
    // To cut down on boilerplate logging in code
    // ----------------------------------------------
    protected fun testDebug(str: String, bTrace: BlockTrace?) {
        if (insideATest) {
            logger.debug("RestartHandler: $str, block causing this: $bTrace")
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
            logger.info("$processName: startBlockchain() - $str: chainId: $chainId")
        }
    }

    private fun startInfoDebug(str: String, processName: BlockchainProcessName, chainId: Long, bcRid: BlockchainRid, bTrace: BlockTrace?) {
        if (logger.isInfoEnabled) {
            logger.info("$processName: startBlockchain() - $str: chainId: $chainId, Blockchain RID: ${bcRid.toHex()}") // We need to print full BC RID so that users can see in INFO logs what it is.
        }
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