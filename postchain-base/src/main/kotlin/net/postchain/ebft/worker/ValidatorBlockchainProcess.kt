// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.base.NetworkAwareTxQueue
import net.postchain.concurrent.util.get
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
) : AbstractBlockchainProcess("validator-${workerContext.processName}", workerContext.engine) {

    companion object : KLogging()

    private val blockDatabase: BaseBlockDatabase
    private val blockManager: BaseBlockManager
    val syncManager: ValidatorSyncManager
    val networkAwareTxQueue: NetworkAwareTxQueue
    val nodeStateTracker = NodeStateTracker()
    val statusManager: StatusManager
    private val appliedConfigSender = AppliedConfigSender(
            workerContext.blockchainConfiguration.configHash,
            workerContext.engine.getBlockQueries(),
            workerContext.communicationManager,
            Duration.ofMillis(workerContext.appConfig.appliedConfigSendInterval())
    )

    init {
        val blockchainConfiguration = workerContext.blockchainConfiguration
        statusManager = BaseStatusManager(
                blockchainConfiguration.signers.size,
                blockchainConfiguration.blockchainContext.nodeID,
                blockchainConfiguration.configHash,
                blockchainEngine.getBlockQueries().getLastBlockHeight().get() + 1
        )

        blockDatabase = BaseBlockDatabase(
                blockchainEngine, blockchainEngine.getBlockQueries(), blockchainConfiguration.blockchainContext.nodeID)

        blockManager = BaseBlockManager(
                workerContext.processName,
                blockDatabase,
                statusManager,
                blockchainEngine.getBlockBuildingStrategy(),
                workerContext
        )

        // Give the SyncManager the BaseTransactionQueue (part of workerContext) and not the network-aware one,
        // because we don't want tx forwarding/broadcasting when received through p2p network
        syncManager = ValidatorSyncManager(
                workerContext,
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
        syncManager.update()
        sleep(20)
    }

    override fun cleanup() {
        appliedConfigSender.shutdown()
        blockDatabase.stop()
        workerContext.shutdown()
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
}
