// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.base.NetworkAwareTxQueue
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.core.BlockchainEngine
import net.postchain.core.NodeStateTracker
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.StatusManager
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import java.lang.Thread.sleep

/**
 * A blockchain instance worker
 *
 * @param workerContext The stuff needed to start working.
 */
class ValidatorBlockchainProcess(val workerContext: WorkerContext) : AbstractBlockchainProcess() {

    companion object : KLogging()

    private val blockDatabase: BaseBlockDatabase
    val syncManager: ValidatorSyncManager
    val networkAwareTxQueue: NetworkAwareTxQueue
    val nodeStateTracker = NodeStateTracker()
    val statusManager: StatusManager

    fun isInFastSyncMode(): Boolean {
        return syncManager.isInFastSync()
    }

    override fun getEngine(): BlockchainEngine {
        return workerContext.engine
    }

    init {
        val bestHeight = getEngine().getBlockQueries().getBestHeight().get()
        statusManager = BaseStatusManager(
                workerContext.signers.size,
                workerContext.nodeId,
                bestHeight + 1)

        blockDatabase = BaseBlockDatabase(
                getEngine(), getEngine().getBlockQueries(), workerContext.nodeId)

        val blockManager = BaseBlockManager(
                workerContext.processName,
                blockDatabase,
                statusManager,
                getEngine().getBlockBuildingStrategy())

        // Give the SyncManager the BaseTransactionQueue (part of workerContext) and not the network-aware one,
        // because we don't want tx forwarding/broadcasting when received through p2p network
        syncManager = ValidatorSyncManager(workerContext,
                statusManager,
                blockManager,
                blockDatabase,
                nodeStateTracker)

        networkAwareTxQueue = NetworkAwareTxQueue(
                getEngine().getTransactionQueue(),
                workerContext.communicationManager)

        statusManager.recomputeStatus()
        startProcess()
    }

    override fun processName(): String = "updateLoop-${workerContext.processName}"

    override fun action() {
        syncManager.update()
        sleep(20)
    }

    /**
     * Stop the postchain node
     */
    override fun shutdown() {
        shutdowDebug("Begin")
        syncManager.shutdown()
        super.shutdown()
        blockDatabase.stop()
        workerContext.shutdown()
        shutdowDebug("End")
    }

    // --------
    // Logging
    // --------

    private fun shutdowDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("${workerContext.processName} shutdown() - $str")
        }
    }

    private fun startUpdateLog(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("${workerContext.processName} startUpdateLoop() -- $str")
        }
    }

    private fun startUpdateErr(str: String, e: Exception) {
        logger.error("${workerContext.processName} startUpdateLoop() -- $str", e)
    }
}