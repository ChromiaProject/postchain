// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.StorageBuilder
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
import net.postchain.core.*
import net.postchain.core.block.BlockTrace
import net.postchain.crypto.sha256Digest
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.LazyDiagnosticValue
import net.postchain.devtools.NameHelper.peerName
import net.postchain.gtv.GtvFactory
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
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
 * If you don't want to wait for startup you can schedule a start via the "startBlockchainAsync()"
 */
open class BaseBlockchainProcessManager(
        protected val postchainContext: PostchainContext,
        protected val blockchainInfrastructure: BlockchainInfrastructure,
        protected val blockchainConfigProvider: BlockchainConfigurationProvider,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : BlockchainProcessManager {

    override val synchronizer = Any()

    val appConfig = postchainContext.appConfig
    val connectionManager = postchainContext.connectionManager
    val nodeDiagnosticContext = postchainContext.nodeDiagnosticContext
    val storage get() = postchainContext.storage
    protected val blockchainProcesses = mutableMapOf<Long, BlockchainProcess>()
    protected val chainIdToBrid = mutableMapOf<Long, BlockchainRid>()
    protected val bridToChainId = mutableMapOf<BlockchainRid, Long>()
    protected val extensions: List<BlockchainProcessManagerExtension> = bpmExtensions
    protected val executor: ExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val scheduledForStart = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    /*
        These chain synchronizers should only be used when starting/stopping chains since we rely on them to figure out if a restart is in progress or not in the after commit handlers.
    */
    private val chainStartAndStopSynchronizers = ConcurrentHashMap<Long, ReentrantLock>()

    // For DEBUG only
    var insideATest = false
    var blockDebug: BlockTrace? = null

    companion object : KLogging()

    /**
     * Put the startup operation of chainId in the [executor]'s work queue.
     *
     * @param chainId is the chain to start.
     * @param loadNextPendingConfig only relevant for PCU. See [net.postchain.managed.ManagedBlockchainConfigurationProvider.getConfigurationFromDataSource]
     */
    protected fun startBlockchainAsync(chainId: Long, bTrace: BlockTrace?, loadNextPendingConfig: Boolean = false) {
        if (!scheduledForStart.add(chainId)) {
            logger.info { "Chain $chainId is already scheduled for start" }
            return
        }
        startBlockchainAsyncInternal(chainId, bTrace, loadNextPendingConfig)
    }

    private fun startBlockchainAsyncInternal(chainId: Long, bTrace: BlockTrace?, loadNextPendingConfig: Boolean) {
        startAsyncInfo("Enqueue async starting of blockchain", chainId)
        executor.execute {
            try {
                startBlockchainInternal(chainId, bTrace, loadNextPendingConfig)
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
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid =
            startBlockchainInternal(chainId, bTrace, false)

    private fun startBlockchainInternal(chainId: Long, bTrace: BlockTrace?, loadNextPendingConfig: Boolean): BlockchainRid {
        chainStartAndStopSynchronizers.getOrPut(chainId) { ReentrantLock() }.withLock {
            val blockchainRid = synchronized(synchronizer) {
                withLoggingContext(
                        NODE_PUBKEY_TAG to appConfig.pubKey,
                        CHAIN_IID_TAG to chainId.toString()
                ) {
                    startDebug("Begin by stopping blockchain", chainId, bTrace)
                    stopBlockchain(chainId, bTrace, true)

                    startInfo("Starting of blockchain", chainId)

                    // We create a new storage instance to open new db connections for each engine
                    val chainStorage = StorageBuilder.buildStorage(postchainContext.appConfig)
                    try {
                        val initialEContext = chainStorage.openWriteConnection(chainId)
                        val blockHeight = blockchainConfigProvider.getActiveBlocksHeight(initialEContext, DatabaseAccess.of(initialEContext))

                        val rawConfigurationData = blockchainConfigProvider.getActiveBlocksConfiguration(initialEContext, chainId, loadNextPendingConfig)
                                ?: throw UserMistake("[${nodeName()}]: Can't start blockchain chainId: $chainId due to configuration is absent")
                        try {
                            val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(
                                    rawConfigurationData, initialEContext, NODE_ID_AUTO, chainId, getBlockchainConfigurationFactory(chainId)
                            ).also {
                                DependenciesValidator.validateBlockchainRids(initialEContext, it.blockchainDependencies)
                                it.initializeModules(postchainContext)
                            }

                            // Initial configuration will be committed immediately
                            if (DatabaseAccess.of(initialEContext).getLastBlockHeight(initialEContext) == -1L) {
                                chainStorage.closeWriteConnection(initialEContext, true)
                            }
                            afterMakeConfiguration(chainId, blockchainConfig)
                            withLoggingContext(BLOCKCHAIN_RID_TAG to blockchainConfig.blockchainRid.toHex()) {
                                startBlockchainImpl(blockchainConfig, chainId, bTrace, chainStorage, initialEContext)
                            }
                            blockchainConfig.blockchainRid
                        } catch (e: Exception) {
                            try {
                                if (hasBuiltInitialBlock(initialEContext)) {
                                    revertConfiguration(chainId, bTrace, chainStorage, initialEContext, blockHeight, rawConfigurationData)
                                }
                            } catch (e: Exception) {
                                logger.warn(e) { "Unable to revert configuration: $e" }
                            }

                            addToErrorQueue(chainId, e)

                            throw e
                        }
                    } catch (e: Exception) {
                        chainStorage.close()
                        throw e
                    } finally {
                        scheduledForStart.remove(chainId)
                    }
                }
            }
            afterStartBlockchain(chainId)
            return blockchainRid
        }
    }

    protected open fun afterMakeConfiguration(chainId: Long, blockchainConfig: BlockchainConfiguration) {}

    protected open fun afterStartBlockchain(chainId: Long) {}

    private fun startBlockchainImpl(blockchainConfig: BlockchainConfiguration, chainId: Long, bTrace: BlockTrace?,
                                    chainStorage: Storage, initialEContext: EContext) {
        val processName = BlockchainProcessName(appConfig.pubKey, blockchainConfig.blockchainRid)
        startDebug("BlockchainConfiguration has been created", processName, chainId, bTrace)

        val afterCommitHandler = buildAfterCommitHandler(chainId, blockchainConfig)
        val restartNotifier = BlockchainRestartNotifier { loadNextPendingConfig ->
            startBlockchainAsync(chainId, bTrace, loadNextPendingConfig)
        }
        val engine = blockchainInfrastructure.makeBlockchainEngine(
                processName,
                blockchainConfig,
                afterCommitHandler,
                chainStorage,
                initialEContext,
                blockchainConfigProvider,
                restartNotifier
        )

        startDebug("BlockchainEngine has been created", processName, chainId, bTrace)

        createAndRegisterBlockchainProcess(
                chainId,
                blockchainConfig,
                processName,
                engine,
                restartNotifier
        )
        logger.debug { "$processName: BlockchainProcess has been launched: chainId: $chainId" }

        startInfoDebug(
                "Blockchain has been started",
                blockchainConfig.signers,
                processName,
                chainId,
                blockchainConfig.blockchainRid,
                bTrace
        )
    }

    private fun hasBuiltInitialBlock(eContext: EContext) = DatabaseAccess.of(eContext).getLastBlockHeight(eContext) > -1L

    private fun revertConfiguration(chainId: Long, bTrace: BlockTrace?, chainStorage: Storage, eContext: EContext, blockHeight: Long,
                                    failedConfig: ByteArray) {
        logger.info("Reverting faulty configuration at height $blockHeight")
        val failedConfigHash = GtvToBlockchainRidFactory.calculateBlockchainRid(GtvFactory.decodeGtv(failedConfig), ::sha256Digest).data

        eContext.conn.rollback() // rollback any DB updates the new and faulty configuration did
        DatabaseAccess.of(eContext).apply {
            addFaultyConfiguration(eContext, FaultyConfiguration(failedConfigHash.wrap(), blockHeight))
            removeConfiguration(eContext, blockHeight)
        }
        chainStorage.closeWriteConnection(eContext, true)

        startBlockchainAsyncInternal(chainId, bTrace, false)
    }

    private fun addToErrorQueue(chainId: Long, e: Exception) {
        withReadConnection(storage, chainId) {
            DatabaseAccess.of(it).getBlockchainRid(it)?.let { brid ->
                nodeDiagnosticContext.blockchainErrorQueue(brid).add(e.message)
            }
        }
    }

    protected open fun getBlockchainConfigurationFactory(chainId: Long): BlockchainConfigurationFactorySupplier =
            DefaultBlockchainConfigurationFactory()

    protected open fun createAndRegisterBlockchainProcess(
            chainId: Long,
            blockchainConfig: BlockchainConfiguration,
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            restartNotifier: BlockchainRestartNotifier
    ) {
        blockchainProcesses[chainId] = blockchainInfrastructure.makeBlockchainProcess(processName, engine, blockchainConfigProvider, restartNotifier)
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
        return synchronized(synchronizer) {
            blockchainProcesses[chainId]
        }
    }

    override fun retrieveBlockchain(blockchainRid: BlockchainRid): BlockchainProcess? {
        return synchronized(synchronizer) {
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
            synchronized(synchronizer) {
                withLoggingContext(
                        NODE_PUBKEY_TAG to appConfig.pubKey,
                        CHAIN_IID_TAG to chainId.toString(),
                        BLOCKCHAIN_RID_TAG to chainIdToBrid[chainId]?.toHex()
                ) {
                    stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
                    stopDebug("Blockchain process has been purged", chainId, bTrace)
                }

                if (!restart) {
                    chainIdToBrid.remove(chainId).also { bridToChainId.remove(it) }
                    chainStartAndStopSynchronizers.remove(chainId)
                }
            }
        }
    }

    protected open fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        val blockchainRid = chainIdToBrid[chainId]
        nodeDiagnosticContext.removeBlockchainData(blockchainRid)
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
        nodeDiagnosticContext.clearBlockchainData()
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
            logger.warn("No blockchain process for $chainId")
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
            logger.debug { "$processName: startBlockchain() -- $str: chainId: $chainId $extraStr" }
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

    private fun startInfoDebug(str: String, signers: List<ByteArray>, processName: BlockchainProcessName, chainId: Long, bcRid: BlockchainRid, bTrace: BlockTrace?) {
        if (logger.isInfoEnabled) {
            logger.info("$processName: startBlockchain() - $str: ${blockchainProcesses[chainId]?.javaClass?.simpleName}, chainId: $chainId, Blockchain RID: ${bcRid.toHex()}, signers: ${signers.map { it.toHex() }}") // We need to print full BC RID so that users can see in INFO logs what it is.
        }
        startDebug(str, processName, chainId, bTrace)
    }

    // Stop BC
    private fun stopDebug(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            logger.debug { "[${nodeName()}]: stopBlockchain() -- $str: chainId: $chainId, block causing the start: $bTrace" }
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