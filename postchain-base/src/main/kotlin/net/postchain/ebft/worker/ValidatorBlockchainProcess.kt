// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import mu.KLogging
import net.postchain.base.NetworkAwareTxQueue
import net.postchain.core.NodeStateTracker
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.StatusManager
import net.postchain.ebft.syncmanager.validator.BlockAndSignatureSender
import net.postchain.ebft.syncmanager.validator.TransactionHandler
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import java.lang.Thread.sleep

/**
 * A blockchain instance worker
 *
 * @param workerContext The stuff needed to start working.
 */
class ValidatorBlockchainProcess(val workerContext: WorkerContext, startWithFastSync: Boolean) : AbstractBlockchainProcess("validator-${workerContext.processName}", workerContext.engine) {

    companion object : KLogging()

    private val blockDatabase: BaseBlockDatabase
    private val blockManager: BaseBlockManager
    private val blockAndSignatureSender: BlockAndSignatureSender
    private val transactionHandler: TransactionHandler
    private val messageJob: Job
    val syncManager: ValidatorSyncManager
    val networkAwareTxQueue: NetworkAwareTxQueue
    val nodeStateTracker = NodeStateTracker()
    val statusManager: StatusManager

    init {
        val bestHeight = blockchainEngine.getBlockQueries().getBestHeight().get()
        val blockchainConfiguration = workerContext.blockchainConfiguration
        statusManager = BaseStatusManager(
                blockchainConfiguration.signers.size,
                blockchainConfiguration.blockchainContext.nodeID,
                bestHeight + 1)

        blockDatabase = BaseBlockDatabase(
                blockchainEngine, blockchainEngine.getBlockQueries(), blockchainConfiguration.blockchainContext.nodeID)

        blockManager = BaseBlockManager(
                workerContext.processName,
                blockDatabase,
                statusManager,
                blockchainEngine.getBlockBuildingStrategy())

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

        blockAndSignatureSender = BlockAndSignatureSender(
                blockDatabase,
                blockManager,
                statusManager,
                workerContext
        )

        transactionHandler = TransactionHandler(
                workerContext.communicationManager,
                workerContext.engine.getTransactionQueue(),
                blockchainConfiguration.getTransactionFactory()
        )

        networkAwareTxQueue = NetworkAwareTxQueue(
                blockchainEngine.getTransactionQueue(),
                workerContext.communicationManager)

        statusManager.recomputeStatus()

        messageJob = CoroutineScope(Dispatchers.Default).launch {
            workerContext.communicationManager.messages
                    .collect {
                        // We do heartbeat check for each network message.
                        while (!workerContext.shouldProcessMessages(getLastBlockTimestamp())) {
                            delay(workerContext.nodeConfig.heartbeatSleepTimeout)
                        }

                        syncManager.dispatchMessage(it)
                    }
        }
    }

    fun isInFastSyncMode() = syncManager.isInFastSync()

    override fun action() {
        // The name awaitPermissionToProcessMessages is kind of misleading now
        if (workerContext.awaitPermissionToProcessMessages(getLastBlockTimestamp()) { !isProcessRunning() }) {
            syncManager.update()
            sleep(20)
        }
    }

    override fun cleanup() {
        transactionHandler.shutdown()
        blockAndSignatureSender.shutdown()
        messageJob.cancel()
        blockDatabase.stop()
        workerContext.shutdown()
    }

    override fun registerDiagnosticData(diagnosticData: MutableMap<DiagnosticProperty, () -> Any>) {
        diagnosticData.putAll(mapOf(
                DiagnosticProperty.BLOCKCHAIN_RID to { workerContext.blockchainConfiguration.blockchainRid.toHex() },
                DiagnosticProperty.BLOCKCHAIN_NODE_TYPE to { DpNodeType.NODE_TYPE_VALIDATOR.prettyName },
                DiagnosticProperty.BLOCKCHAIN_CURRENT_HEIGHT to syncManager::getHeight
        ))
    }

    private fun getLastBlockTimestamp(): Long {
        /**
         * NB: The field blockManager.lastBlockTimestamp will be set to non-null value
         * after the first block db operation. So we read lastBlockTimestamp value from db
         * until blockManager.lastBlockTimestamp is non-null.
         */
        return blockManager.lastBlockTimestamp
                ?: workerContext.engine.getBlockQueries().getLastBlockTimestamp().get()
    }
}
