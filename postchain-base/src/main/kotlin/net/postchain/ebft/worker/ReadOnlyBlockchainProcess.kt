// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.concurrent.util.get
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
import net.postchain.ebft.syncmanager.common.SnapshotSynchronizer
import net.postchain.ebft.syncmanager.common.SyncMethod
import net.postchain.ebft.syncmanager.common.SyncParameters
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

class ReadOnlyBlockchainProcess(
        private val workerContext: WorkerContext,
        private val blockchainState: BlockchainState,
        private val transactionForwarder: TransactionForwarder? = null,
) : AbstractBlockchainProcess(
        "${if (blockchainState == BlockchainState.PAUSED) "paused-" else ""}replica-c${workerContext.blockchainConfiguration.chainID}",
        workerContext.engine
) {

    companion object : KLogging()

    private var txChecker: ScheduledExecutorService? = null

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

    override fun start() {
        super.start()
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

            txChecker = Executors.newSingleThreadScheduledExecutor(
                    ThreadFactoryBuilder().setNameFormat("$processName-txStatusChecker").build()
            ).apply {
                scheduleAtFixedRate({
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
    }

    /**
     * For read only nodes we don't want to fast sync forever.
     * When the nodes are drained we move to slow sync instead.
     */
    override fun action() {
        // TODO: Maybe we need to check latest rather than current config?
        val snapshotSyncEnabled = BlockchainConfigurationData.snapshotSyncEnabled(workerContext.blockchainConfiguration.rawConfig)
        withLoggingContext(loggingContext) {
            if (params.slowSyncEnabled) {
                logger.debug { "Using slow sync for read only bc process" }
                // TODO: We should check if snapshots are enabled for this bc
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
                // TODO: We should check if snapshots are enabled for this bc
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
            txChecker?.shutdown()
            blockDatabase.stop()
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
            snapshotSynchronizer.blockHeaderContextData.values.map {
                SnapshotSyncContextStatus(it.contextId, localMaxIds[it.contextId], it.datumIdMax)
            }
        }
        else -> null
    }
}
