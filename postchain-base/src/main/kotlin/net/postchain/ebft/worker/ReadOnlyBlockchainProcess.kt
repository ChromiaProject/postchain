// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainProcessParams
import net.postchain.core.BlockchainState
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpBlockchainNodeState
import net.postchain.debug.DpNodeType
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.LazyDiagnosticValue
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.PersistOnlyBlockWriter
import net.postchain.ebft.rest.contract.SnapshotSyncContextStatus
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.ebft.syncmanager.common.KnownState
import net.postchain.ebft.syncmanager.common.PeerStatuses
import net.postchain.ebft.syncmanager.common.SlowSynchronizer
import net.postchain.ebft.syncmanager.common.SnapshotSyncEvents
import net.postchain.ebft.syncmanager.common.SnapshotSynchronizer
import net.postchain.ebft.syncmanager.common.SyncMethod
import net.postchain.ebft.syncmanager.common.SyncParameters
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.syncmanager.readonly.ForceReadOnlyMessageProcessor
import net.postchain.gtv.mapper.toObject
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

open class ReadOnlyBlockchainProcess(
        private val workerContext: WorkerContext,
        private val blockchainState: BlockchainState,
        private val transactionForwarder: TransactionForwarder? = null,
        private val processParams: BlockchainProcessParams? = null,
        private val initialSyncMonitorDelay: Long = 30000
) : AbstractBlockchainProcess(
        "${if (blockchainState == BlockchainState.PAUSED) "paused-" else ""}replica-c${workerContext.blockchainConfiguration.chainID}",
        workerContext.engine
) {

    companion object : KLogging()

    private var singleExecutor: ScheduledExecutorService? = null

    val isForwardingReplica = transactionForwarder != null

    private val myPubKey = workerContext.appConfig.pubKey

    private val loggingContext = mapOf(
            CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString(),
            BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex()
    )

    private val blockDatabase = BaseBlockDatabase(
            loggingContext, blockchainEngine, blockchainEngine.getBlockQueries(), workerContext.nodeDiagnosticContext, NODE_ID_READ_ONLY
    )

    private val persistOnlyBlockWriter = PersistOnlyBlockWriter(
            loggingContext,
            NODE_ID_READ_ONLY,
            blockchainEngine
    )

    private val params = SyncParameters.fromAppConfig(workerContext.appConfig)

    protected val restartNotified = AtomicBoolean(false)
    protected var syncMonitorLastHeight: Long? = null
    protected var syncMonitorLastActivityTime: Instant? = null

    private val fastSynchronizer = FastSynchronizer(
            workerContext,
            blockDatabase,
            params,
            PeerStatuses(params.syncPeerParameters),
            ::isProcessRunning,
            RateLimitConfiguration.fromAppConfig(workerContext.appConfig)
    )

    private val slowSynchronizer = SlowSynchronizer(
            workerContext,
            blockDatabase,
            params,
            ::isProcessRunning,
            RateLimitConfiguration.fromAppConfig(workerContext.appConfig)
    )

    private val snapshotSynchronizer = SnapshotSynchronizer(
            workerContext,
            persistOnlyBlockWriter,
            params,
            PeerStatuses(params.snapshotSyncPeerParameters),
            ::isProcessRunning,
            RateLimitConfiguration.fromAppConfig(workerContext.appConfig)
    )

    private var syncMethod = SyncMethod.NOT_SYNCING

    protected open val forceReadOnlyMessageProcessor = ForceReadOnlyMessageProcessor(
            workerContext.engine.getBlockQueries(), workerContext.communicationManager,
            workerContext.engine.getBlockQueries().getLastBlockHeight().get(),
            RateLimitConfiguration.fromAppConfig(workerContext.appConfig))

    override fun isExpectingNewBlocks(): Boolean {
        return processParams == null || processParams.syncEnabled
    }

    override fun start() {
        super.start()

        singleExecutor = Executors.newSingleThreadScheduledExecutor(
                ThreadFactoryBuilder().setNameFormat("$processName-scheduled").build()
        ).apply {
            if (blockchainState == BlockchainState.PAUSED && isExpectingNewBlocks()) {
                val maxBlockTime = (workerContext.blockchainConfiguration.rawConfig[KEY_BLOCKSTRATEGY]
                        ?.toObject<BaseBlockBuildingStrategyConfigurationData>()
                        ?: BaseBlockBuildingStrategyConfigurationData.default).maxBlockTime
                val syncBlockTimeout = maxBlockTime * 3
                scheduleAtFixedRate({
                    if (syncMonitorLastActivityTime == null) {
                        if (syncMethod != SyncMethod.NOT_SYNCING) {
                            syncMonitorLastActivityTime = Instant.now()
                        }
                    } else if (!restartNotified.get()) {
                        val height = workerContext.engine.getBlockQueries().getLastBlockHeight().get()
                        if (syncMonitorLastHeight == height) {
                            if (Instant.now().minusMillis(syncBlockTimeout).isAfter(syncMonitorLastActivityTime)) {
                                withLoggingContext(loggingContext) {
                                    logger.info { "No new blocks. Restarting without sync enabled " }
                                }
                                restartNotified.set(true)
                                workerContext.restartNotifier.notifyRestart(null, false)
                            }
                        } else {
                            syncMonitorLastHeight = height
                        }
                    }
                }, initialSyncMonitorDelay, maxBlockTime, TimeUnit.MILLISECONDS)
            }
        }

        if (transactionForwarder != null) {
            thread(name = "$processName-txForwarder", start = true) {
                withLoggingContext(loggingContext) {
                    while (isProcessRunning()) {
                        workerContext.engine.getTransactionQueue().takeTransaction(1.seconds)?.let {
                            try {
                                transactionForwarder.forward(it)
                            } catch (e: UserMistake) {
                                logger.warn("Unable to forward transaction ${it.getRID()}: ${e.message}")
                                workerContext.engine.getTransactionQueue().rejectTransaction(it, e)
                            } catch (e: Exception) {
                                logger.warn(e) { "Unable to forward transaction ${it.getRID()}: $e" }
                                workerContext.engine.getTransactionQueue().rejectTransaction(it, e)
                            }
                        }
                    }
                }
            }

            singleExecutor?.scheduleAtFixedRate({
                    for (tx in workerContext.engine.getTransactionQueue().takenTransactions()) {
                        try {
                            val apiStatus = transactionForwarder.checkStatus(tx)
                            when (apiStatus.status) {
                                TransactionStatus.REJECTED.status -> {
                                    workerContext.engine.getTransactionQueue().rejectTransaction(tx, apiStatus.rejectReason?.let {
                                        UserMistake(it)
                                    } ?: UserMistake("Unknown reason from API"), apiStatus.rejectTimestamp?.let { Instant.ofEpochMilli(it) })
                                }

                                // Wait for next iteration for CONFIRMED, WAITING and UNKNOWN
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Unable to check transaction status ${tx.getRID()}: $e" }
                        }
                    }
                },
                        /* initialDelay = */ 0,
                        /* period = */ 1,
                        TimeUnit.SECONDS)
        }
    }

    /**
     * For read only nodes we don't want to fast sync forever.
     * When the nodes are drained we move to slow sync instead.
     */
    override fun action() {
        val snapshotSyncEnabled = BlockchainConfigurationData.snapshotSyncEnabled(workerContext.blockchainConfiguration.rawConfig)
        withLoggingContext(loggingContext) {
            if (!isExpectingNewBlocks()) {
                logger.debug { "Syncing is disabled for this read only process" }
                while (isProcessRunning()) {
                    forceReadOnlyMessageProcessor.processMessages()
                    Thread.sleep(100)
                }
            } else if (params.slowSyncEnabled) {
                logger.debug { "Using slow sync for read only bc process" }
                if (snapshotSyncEnabled) {
                    syncMethod = SyncMethod.SNAPSHOT_SYNC
                    snapshotSynchronizer.trySnapshotSync()
                }
                syncMethod = SyncMethod.FAST_SYNC
                fastSynchronizer.syncUntilResponsiveNodesDrained()
                // Move to slow sync and proceed until shutdown
                syncMethod = SyncMethod.SLOW_SYNC
                slowSynchronizer.syncUntil()
                syncMethod = SyncMethod.NOT_SYNCING
            } else {
                logger.debug { "Using fast sync for read only bc process" }
                if (snapshotSyncEnabled) {
                    syncMethod = SyncMethod.SNAPSHOT_SYNC
                    snapshotSynchronizer.trySnapshotSync()
                }
                syncMethod = SyncMethod.FAST_SYNC
                fastSynchronizer.syncUntil { !isProcessRunning() }
                syncMethod = SyncMethod.NOT_SYNCING
            }
        }
    }

    override fun cleanup() {
        withLoggingContext(loggingContext) {
            singleExecutor?.shutdown()
            blockDatabase.stop()
            persistOnlyBlockWriter.stop()
            workerContext.shutdown()
        }
    }

    override fun registerDiagnosticData(diagnosticData: DiagnosticData) {
        super.registerDiagnosticData(diagnosticData)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_TYPE] = EagerDiagnosticValue(DpNodeType.NODE_TYPE_REPLICA.prettyName)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATE] = EagerDiagnosticValue(
                when (blockchainState) {
                    BlockchainState.PAUSED -> DpBlockchainNodeState.PAUSED_READ_ONLY
                    BlockchainState.UNARCHIVING -> DpBlockchainNodeState.UNARCHIVING_READ_ONLY
                    else -> DpBlockchainNodeState.RUNNING_READ_ONLY
                }
        )
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATUS] = LazyDiagnosticValue {
            StateNodeStatus(myPubKey, DpNodeType.NODE_TYPE_REPLICA.name, syncMethod.name, currentBlockHeight(),
                    snapshotSyncContextStatus = currentSnapshotSyncContextStatus())
        }
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_PEERS_STATUSES] = LazyDiagnosticValue {
            val peerStates: List<Pair<String, KnownState>> = when (syncMethod) {
                SyncMethod.FAST_SYNC -> fastSynchronizer.peerStatuses.peersStates
                SyncMethod.SLOW_SYNC -> slowSynchronizer.peerStatuses.peersStates
                else -> emptyList()
            }
            peerStates.map { (pubKey, knownState) -> StateNodeStatus(pubKey, "PEER", knownState.state.name) }
        }
    }

    override fun isSigner() = false
    override fun getBlockchainState(): BlockchainState = blockchainState

    override fun currentBlockHeight(): Long = when (syncMethod) {
        SyncMethod.FAST_SYNC -> fastSynchronizer.blockHeight.get()
        SyncMethod.SLOW_SYNC -> slowSynchronizer.blockHeight.get()
        SyncMethod.SNAPSHOT_SYNC -> blockchainEngine.getBlockQueries().getLastBlockHeight().get() // TODO: I think this is fine? Progress can't be measured in block height anyway.
        SyncMethod.NOT_SYNCING -> blockchainEngine.getBlockQueries().getLastBlockHeight().get()
        SyncMethod.LOCAL_DB -> blockchainEngine.getBlockQueries().getLastBlockHeight().get()
    }

    private fun currentSnapshotSyncContextStatus(): List<SnapshotSyncContextStatus>? = when (syncMethod) {
        SyncMethod.SNAPSHOT_SYNC -> {
            val localMaxIds = blockchainEngine.getBlockQueries().getSnapshotContextMaxIds(Long.MAX_VALUE).get()
            snapshotSynchronizer.getContextStates().map {
                SnapshotSyncContextStatus(it.contextId, localMaxIds[it.contextId], it.maxDatumId)
            }
        }
        else -> null
    }

    fun getSnapshotSyncEvents(): SnapshotSyncEvents {
        return snapshotSynchronizer.snapshotSyncEvents
    }
}
