// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.base.configuration.FaultyConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BeforeCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactorySupplier
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.BlockchainState
import net.postchain.core.DefaultBlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.block.BlockTrace
import net.postchain.crypto.sha256Digest
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.ErrorDiagnosticValue
import net.postchain.debug.LazyDiagnosticValue
import net.postchain.devtools.NameHelper.peerName
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.metrics.BlockchainProcessManagerMetrics
import java.util.Collections
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
 * If you don't want to wait for startup you can schedule a start via the "startBlockchainAsync()"
 */
open class BaseBlockchainProcessManager(
        protected val postchainContext: PostchainContext,
        protected val blockchainInfrastructure: BlockchainInfrastructure,
        protected val blockchainConfigProvider: BlockchainConfigurationProvider,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : BlockchainProcessManager {

    val processLock = ReentrantLock()

    val appConfig = postchainContext.appConfig
    val connectionManager = postchainContext.connectionManager
    val nodeDiagnosticContext = postchainContext.nodeDiagnosticContext
    val sharedStorage get() = postchainContext.sharedStorage
    val blockBuilderStorage get() = postchainContext.blockBuilderStorage
    protected val blockchainProcesses = mutableMapOf<Long, BlockchainProcess>()
    protected val chainIdToBrid = mutableMapOf<Long, BlockchainRid>()
    protected val bridToChainId = mutableMapOf<BlockchainRid, Long>()
    protected val extensions: List<BlockchainProcessManagerExtension> = bpmExtensions
    protected val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scheduledForStart = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val scheduledForStop = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    /*
        These chain synchronizers should only be used when starting/stopping chains since we rely on them to figure out if a restart is in progress or not in the after commit handlers.
    */
    private val chainStartAndStopSynchronizers = ConcurrentHashMap<Long, ReentrantLock>()

    // For DEBUG only
    var insideATest = false
    var blockDebug: BlockTrace? = null

    private val metrics = BlockchainProcessManagerMetrics(this)

    companion object : KLogging()

    /**
     * Put the startup operation of chainId in the [executor]'s work queue.
     *
     * @param chainId is the chain to start.
     * @param loadNextPendingConfig see [net.postchain.managed.ManagedBlockchainConfigurationProvider.getConfigurationFromDataSource]
     */
    protected fun startBlockchainAsync(chainId: Long, bTrace: BlockTrace?, loadNextPendingConfig: Boolean = false) {
        if (!scheduledForStart.add(chainId)) {
            logger.info { "Chain $chainId is already scheduled for start" }
            return
        }
        startBlockchainAsyncInternal(chainId, bTrace, loadNextPendingConfig)
    }

    private fun startBlockchainAsyncInternal(chainId: Long, bTrace: BlockTrace?, loadNextPendingConfig: Boolean) {
        logger.info { "startBlockchainAsync() - Enqueue async starting of blockchain with chainId: $chainId" }
        executor.execute {
            withLoggingContext(CHAIN_IID_TAG to chainId.toString()) {
                try {
                    startBlockchainInternal(chainId, bTrace, loadNextPendingConfig)
                } catch (e: Exception) {
                    logger.error(e) { e.message }
                }
            }
        }
    }

    /**
     * Put the stop operation of chainId in the [executor]'s work queue.
     *
     * @param chainId is the chain to stop.
     */
    protected fun stopBlockchainAsync(chainId: Long, bTrace: BlockTrace?) {
        if (!scheduledForStop.add(chainId)) {
            logger.info { "Chain $chainId is already scheduled for stop" }
            return
        }

        logger.info { "stopBlockchainAsync() - Enqueue async stopping of blockchain with chainId: $chainId" }
        executor.execute {
            withLoggingContext(CHAIN_IID_TAG to chainId.toString()) {
                try {
                    stopBlockchain(chainId, bTrace)
                } catch (e: Exception) {
                    logger.error(e) { e.message }
                }
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
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid = withLoggingContext(CHAIN_IID_TAG to chainId.toString()) {
        startBlockchainInternal(chainId, bTrace, false)
    }

    private fun startBlockchainInternal(chainId: Long, bTrace: BlockTrace?, loadNextPendingConfig: Boolean): BlockchainRid {
        chainStartAndStopSynchronizers.getOrPut(chainId) { ReentrantLock() }.withLock {
            val blockchainRid = processLock.withLock {
                startDebug("Begin by stopping blockchain", bTrace)
                stopBlockchain(chainId, bTrace, true)

                logger.info("Starting of blockchain")

                try {
                    val initialEContext = blockBuilderStorage.openWriteConnection(chainId)
                    val blockHeight = blockchainConfigProvider.getActiveBlocksHeight(initialEContext, DatabaseAccess.of(initialEContext))

                    val rawConfigurationData = blockchainConfigProvider.getActiveBlockConfiguration(initialEContext, chainId, loadNextPendingConfig)
                            ?: throw UserMistake("Can't start blockchain chainId: $chainId due to configuration is absent")
                    val bcConfigOptions = blockchainConfigProvider.getActiveBlockConfigurationOptions(initialEContext, chainId)
                    try {
                        val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(
                                rawConfigurationData, initialEContext, NODE_ID_AUTO, chainId, getBlockchainConfigurationFactory(chainId), bcConfigOptions
                        ).also {
                            DependenciesValidator.validateBlockchainRids(initialEContext, it.blockchainDependencies)
                            it.initializeModules(postchainContext)
                        }

                        // Initial configuration will be committed immediately
                        if (DatabaseAccess.of(initialEContext).getLastBlockHeight(initialEContext) == -1L) {
                            blockBuilderStorage.closeWriteConnection(initialEContext, true)
                        }
                        afterMakeConfiguration(chainId, blockchainConfig)
                        withLoggingContext(BLOCKCHAIN_RID_TAG to blockchainConfig.blockchainRid.toHex()) {
                            startBlockchainImpl(blockchainConfig, chainId, bTrace, initialEContext)
                        }
                        blockchainConfig.blockchainRid
                    } catch (e: Exception) {
                        try {
                            val eContext = if (initialEContext.conn.isClosed) blockBuilderStorage.openWriteConnection(chainId) else initialEContext
                            val configHash = GtvToBlockchainRidFactory.calculateBlockchainRid(GtvDecoder.decodeGtv(rawConfigurationData), ::sha256Digest).data
                            if (hasBuiltInitialBlock(eContext) && !hasBuiltBlockWithConfig(eContext, blockHeight, configHash)) {
                                revertConfiguration(chainId, bTrace, eContext, blockHeight, rawConfigurationData)
                            } else {
                                blockBuilderStorage.closeWriteConnection(eContext, false)
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Unable to revert configuration: $e" }
                        }

                        addToErrorQueue(chainId, e)

                        throw e
                    }
                } finally {
                    scheduledForStart.remove(chainId)
                }
            }
            afterStartBlockchain(chainId)
            return blockchainRid
        }
    }

    protected open fun afterMakeConfiguration(chainId: Long, blockchainConfig: BlockchainConfiguration) {}

    protected open fun afterStartBlockchain(chainId: Long) {}

    private fun startBlockchainImpl(blockchainConfig: BlockchainConfiguration, chainId: Long, bTrace: BlockTrace?,
                                    initialEContext: EContext) {
        startDebug("BlockchainConfiguration has been created", bTrace)

        val beforeCommitHandler = buildBeforeCommitHandler(blockchainConfig)
        val afterCommitHandler = buildAfterCommitHandler(chainId, blockchainConfig)
        val restartNotifier = BlockchainRestartNotifier { loadNextPendingConfig ->
            startBlockchainAsync(chainId, bTrace, loadNextPendingConfig)
        }
        val engine = blockchainInfrastructure.makeBlockchainEngine(
                blockchainConfig,
                beforeCommitHandler,
                afterCommitHandler,
                blockBuilderStorage,
                sharedStorage,
                initialEContext,
                blockchainConfigProvider,
                restartNotifier
        )

        startDebug("BlockchainEngine has been created", bTrace)

        createAndRegisterBlockchainProcess(
                chainId,
                blockchainConfig,
                engine,
                restartNotifier,
                getBlockchainState(chainId, blockchainConfig.blockchainRid)
        )
        logger.debug("BlockchainProcess has been launched")

        logger.info {
            // We need to print full BC RID so that users can see in INFO logs what it is.
            val process = blockchainProcesses[chainId]
            "startBlockchain() - Blockchain has been started: ${process?.javaClass?.simpleName}:${process?.getBlockchainState()}," +
                    " full blockchain RID: ${blockchainConfig.blockchainRid.toHex()}, signers: ${blockchainConfig.signers.map { it.toHex() }}"
        }
    }

    protected open fun getBlockchainState(chainId: Long, blockchainRid: BlockchainRid): BlockchainState = BlockchainState.RUNNING

    private fun hasBuiltInitialBlock(eContext: EContext) = DatabaseAccess.of(eContext).getLastBlockHeight(eContext) > -1L

    private fun hasBuiltBlockWithConfig(eContext: EContext, blockHeight: Long, configHash: ByteArray): Boolean {
        val db = DatabaseAccess.of(eContext)
        val configIsSaved = db.configurationHashExists(eContext, configHash)
        return if (!configIsSaved) {
            false
        } else {
            val configHeight = db.findConfigurationHeightForBlock(eContext, blockHeight) ?: 0
            blockHeight > configHeight
        }
    }

    private fun revertConfiguration(chainId: Long, bTrace: BlockTrace?, eContext: EContext, blockHeight: Long,
                                    failedConfig: ByteArray) {
        logger.info("Reverting faulty configuration at height $blockHeight")
        val failedConfigHash = GtvToBlockchainRidFactory.calculateBlockchainRid(GtvFactory.decodeGtv(failedConfig), ::sha256Digest).data

        eContext.conn.rollback() // rollback any DB updates the new and faulty configuration did
        DatabaseAccess.of(eContext).apply {
            addFaultyConfiguration(eContext, FaultyConfiguration(failedConfigHash.wrap(), blockHeight))
            removeConfiguration(eContext, blockHeight)
        }
        blockBuilderStorage.closeWriteConnection(eContext, true)

        startBlockchainAsyncInternal(chainId, bTrace, false)
    }

    private fun addToErrorQueue(chainId: Long, e: Exception) {
        withReadConnection(sharedStorage, chainId) {
            DatabaseAccess.of(it).getBlockchainRid(it)?.let { brid ->
                nodeDiagnosticContext.blockchainErrorQueue(brid).add(
                        ErrorDiagnosticValue(
                                e.message ?: "Failed to start blockchain for chainId: $chainId",
                                System.currentTimeMillis()
                        )
                )
            }
        }
    }

    protected open fun getBlockchainConfigurationFactory(chainId: Long): BlockchainConfigurationFactorySupplier =
            DefaultBlockchainConfigurationFactory()

    protected open fun createAndRegisterBlockchainProcess(
            chainId: Long,
            blockchainConfig: BlockchainConfiguration,
            engine: BlockchainEngine,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ) {
        blockchainProcesses[chainId] = blockchainInfrastructure.makeBlockchainProcess(engine, blockchainConfigProvider, restartNotifier, blockchainState)
                .also {
                    val diagnosticData = nodeDiagnosticContext.blockchainData(blockchainConfig.blockchainRid).also { data ->
                        data[DiagnosticProperty.BLOCKCHAIN_LAST_HEIGHT] = LazyDiagnosticValue { engine.getBlockQueries().getLastBlockHeight().get() }
                        data[DiagnosticProperty.BLOCKCHAIN_NODE_PEERS] = LazyDiagnosticValue { connectionManager.getNodesTopology(chainId) }
                    }
                    it.registerDiagnosticData(diagnosticData)
                    extensions.forEach { ext -> ext.connectProcess(it) }
                    chainIdToBrid[chainId] = blockchainConfig.blockchainRid
                    bridToChainId[blockchainConfig.blockchainRid] = chainId
                }
    }

    override fun retrieveBlockchain(chainId: Long): BlockchainProcess? {
        return processLock.withLock {
            blockchainProcesses[chainId]
        }
    }

    override fun retrieveBlockchain(blockchainRid: BlockchainRid): BlockchainProcess? {
        return processLock.withLock {
            bridToChainId[blockchainRid]?.let { blockchainProcesses[it] }
        }
    }

    /**
     * Will call "shutdown()" on the [BlockchainProcess] and remove it from the list.
     *
     * @param chainId is the chain to be stopped.
     */
    override fun stopBlockchain(chainId: Long, bTrace: BlockTrace?, restart: Boolean) {
        chainStartAndStopSynchronizers[chainId]?.withLock {
            processLock.withLock {
                withLoggingContext(
                        CHAIN_IID_TAG to chainId.toString(),
                        BLOCKCHAIN_RID_TAG to chainIdToBrid[chainId]?.toHex()
                ) {
                    try {
                        stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
                        stopDebug("Blockchain process has been purged", bTrace)

                        if (!restart) {
                            val brid = chainIdToBrid.remove(chainId)
                            if (brid != null) {
                                bridToChainId.remove(brid)
                            } else {
                                logger.error("No blockchain RID mapping for chainId: $chainId was found when stopping blockchain")
                            }

                            chainStartAndStopSynchronizers.remove(chainId)
                        }
                    } finally {
                        scheduledForStop.remove(chainId)
                    }
                }
            }
        }
    }

    fun numberOfBlockchains(): Int = processLock.withLock {
        blockchainProcesses.size
    }

    protected open fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        val blockchainRid = chainIdToBrid[chainId]
        if (!restart) {
            nodeDiagnosticContext.removeBlockchainData(blockchainRid)
        } else {
            blockchainRid?.let { nodeDiagnosticContext.blockchainErrorQueue(it).clear() }
        }
        blockchainProcesses.remove(chainId)?.also {
            stopInfoDebug("Stopping of blockchain", bTrace)
            extensions.forEach { ext -> ext.disconnectProcess(it) }
            if (restart) {
                blockchainInfrastructure.restartBlockchainProcess(it)
            } else {
                blockchainInfrastructure.exitBlockchainProcess(it)
            }
            it.shutdown()
            stopInfoDebug("Stopping blockchain, shutdown complete", bTrace)
        }
    }

    override fun shutdown() {
        logger.debug("Stopping BlockchainProcessManager")
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS)

        blockchainProcesses.values.forEach {
            blockchainInfrastructure.exitBlockchainProcess(it)
            extensions.forEach { ext -> ext.disconnectProcess(it) }
            it.shutdown()
        }
        blockchainProcesses.clear()
        nodeDiagnosticContext.clearBlockchainData()
        chainIdToBrid.clear()
        bridToChainId.clear()
        metrics.close()
        logger.debug("Stopped BlockchainProcessManager")
    }

    /**
     * Actions to be taken before committing a block.
     * These will be performed in the same transaction as the block.
     */
    protected open fun buildBeforeCommitHandler(blockchainConfig: BlockchainConfiguration): BeforeCommitHandler {
        return { _, _ -> }
    }

    /**
     * Define what actions should be taken after block commit. In our case:
     * 1) trigger bp extensions,
     * 2) checks for configuration changes, and then does an async reboot of the given chain.
     *
     * @param chainId - the chain we should build the [AfterCommitHandler] for
     * @return a newly created [AfterCommitHandler]. This method will be much more complex is
     * the subclass [net.postchain.managed.ManagedBlockchainProcessManager].
     */
    protected open fun buildAfterCommitHandler(chainId: Long, blockchainConfig: BlockchainConfiguration): AfterCommitHandler {
        return { bTrace, blockHeight, _ ->
            try {
                // If chain is already being stopped/restarted by another thread we will not get the lock and may return
                if (!tryAcquireChainLock(chainId)) {
                    false
                } else {
                    invokeAfterCommitHooks(chainId, blockHeight)

                    val doRestart = isConfigurationChanged(chainId)
                    if (doRestart) {
                        testDebug("BaseBlockchainProcessManager, need restart of: $chainId", bTrace)
                        startBlockchainAsync(chainId, bTrace)
                    }
                    doRestart
                }
            } finally {
                releaseChainLock(chainId)
            }
        }
    }

    protected open fun invokeAfterCommitHooks(chainId: Long, blockHeight: Long) {
        val blockchainProcess = blockchainProcesses[chainId]
        if (blockchainProcess != null) {
            for (e in extensions) {
                e.afterCommit(blockchainProcess, blockHeight)
            }
        } else {
            logger.warn("No blockchain process found")
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
            "Topology: ${prettyTopology.values}"
        }
    }

    protected fun isConfigurationChanged(chainId: Long): Boolean {
        return withReadConnection(blockBuilderStorage, chainId) { eContext ->
            val isSigner = blockchainProcesses[chainId]?.isSigner() ?: false
            blockchainConfigProvider.activeBlockNeedsConfigurationChange(eContext, chainId, isSigner)
        }
    }

    protected fun tryAcquireChainLock(chainId: Long): Boolean {
        return chainStartAndStopSynchronizers[chainId]?.tryLock()
                ?: throw ProgrammerMistake("No lock instance exists for chain $chainId")
    }

    protected fun releaseChainLock(chainId: Long) {
        chainStartAndStopSynchronizers[chainId]?.apply {
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
            logger.debug { "RestartHandler: $str, block causing this: $bTrace" }
        }
    }

    // Start BC
    private fun startDebug(str: String, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            val extraStr = if (bTrace != null) {
                ", block causing the start: $bTrace"
            } else {
                ""
            }
            logger.debug("startBlockchain() -- $str $extraStr")
        }
    }

    // Stop BC
    private fun stopDebug(str: String, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            logger.debug { "stopBlockchain() -- $str: block causing the start: $bTrace" }
        }
    }

    private fun stopInfoDebug(str: String, bTrace: BlockTrace?) {
        logger.info { "stopBlockchain() - $str" }
        stopDebug(str, bTrace)
    }

}