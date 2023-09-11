// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.NetworkAwareTxQueue
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainState
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.LazyDiagnosticValue
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.NodeStateTracker
import net.postchain.ebft.StatusManager
import net.postchain.ebft.rest.contract.toStateNodeStatus
import net.postchain.ebft.syncmanager.validator.AppliedConfigSender
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.metrics.NodeStatusMetrics
import java.lang.Thread.sleep
import java.time.Duration

/**
 * A blockchain instance worker
 *
 * @param workerContext The stuff needed to start working.
 */
class ValidatorBlockchainProcess(
        val workerContext: WorkerContext,
        startWithFastSync: Boolean
) : AbstractBlockchainProcess("validator-c${workerContext.blockchainConfiguration.chainID}", workerContext.engine) {

    companion object : KLogging()

    private val blockDatabase: BaseBlockDatabase
    private val blockManager: BaseBlockManager
    val syncManager: ValidatorSyncManager
    val networkAwareTxQueue: NetworkAwareTxQueue
    private val nodeStateTracker = NodeStateTracker()
    val statusManager: StatusManager
    private val appliedConfigSender = AppliedConfigSender(
            workerContext,
            Duration.ofMillis(workerContext.appConfig.appliedConfigSendInterval())
    )

    private val loggingContext = mapOf(
            CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString(),
            BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex()
    )

    init {
        val blockchainConfiguration = workerContext.blockchainConfiguration
        statusManager = BaseStatusManager(
                blockchainConfiguration.signers,
                blockchainConfiguration.blockchainContext.nodeID,
                blockchainEngine.getBlockQueries().getLastBlockHeight().get() + 1,
                NodeStatusMetrics()
        )

        blockDatabase = BaseBlockDatabase(
                loggingContext, blockchainEngine, blockchainEngine.getBlockQueries(), workerContext.nodeDiagnosticContext, blockchainConfiguration.blockchainContext.nodeID)

        blockManager = BaseBlockManager(
                blockDatabase,
                statusManager,
                blockchainEngine.getBlockBuildingStrategy(),
                workerContext
        )

        // Give the SyncManager the BaseTransactionQueue (part of workerContext) and not the network-aware one,
        // because we don't want tx forwarding/broadcasting when received through p2p network
        syncManager = ValidatorSyncManager(
                workerContext,
                loggingContext,
                statusManager,
                blockManager,
                blockDatabase,
                nodeStateTracker,
                ::isProcessRunning,
                startWithFastSync
        )

        networkAwareTxQueue = NetworkAwareTxQueue(
                blockchainEngine.getTransactionQueue(),
                workerContext.communicationManager)

        statusManager.recomputeStatus()
    }

    fun isInFastSyncMode() = syncManager.isInFastSync()

    override fun action() {
        withLoggingContext(loggingContext) {
            syncManager.update()
        }
        sleep(20)
    }

    override fun cleanup() {
        withLoggingContext(loggingContext) {
            appliedConfigSender.shutdown()
            blockDatabase.stop()
            workerContext.shutdown()
        }
    }

    override fun registerDiagnosticData(diagnosticData: DiagnosticData) {
        super.registerDiagnosticData(diagnosticData)
        val myNodeIndex = statusManager.getMyIndex()
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_TYPE] = EagerDiagnosticValue(DpNodeType.NODE_TYPE_VALIDATOR.prettyName)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATUS] = LazyDiagnosticValue {
            val errorQueue = workerContext.nodeDiagnosticContext.blockchainErrorQueue(workerContext.blockchainConfiguration.blockchainRid)
            val nodeRid = syncManager.validatorAtIndex(myNodeIndex)
            nodeStateTracker.myStatus?.toStateNodeStatus(nodeRid.toHex(), errorQueue)
        }
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_PEERS_STATUSES] = LazyDiagnosticValue {
            nodeStateTracker.nodeStatuses
                    ?.withIndex()
                    ?.filter { it.index != myNodeIndex }
                    ?.map {
                        val nodeRid = syncManager.validatorAtIndex(it.index)
                        it.value.toStateNodeStatus(nodeRid.toHex())
                    }?.toList()
        }
    }

    override fun isSigner(): Boolean = !syncManager.isInFastSync()
    override fun getBlockchainState(): BlockchainState = BlockchainState.RUNNING

    override fun currentBlockHeight(): Long = syncManager.currentBlockHeight() ?: blockchainEngine.getBlockQueries().getLastBlockHeight().get()
}
