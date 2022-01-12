// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.base.NetworkAwareTxQueue
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.core.NodeStateTracker
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import java.lang.Thread.sleep

/**
 * A blockchain instance worker
 *
 * @param workerContext The stuff needed to start working.
 */
class ValidatorBlockchainProcess(val workerContext: WorkerContext) : AbstractBlockchainProcess(workerContext.processName.toString(), workerContext.engine) {

    companion object : KLogging()
    val nodeStateTracker = NodeStateTracker()

    val statusManager = BaseStatusManager(
        workerContext.signers.size,
        workerContext.nodeId,
        blockchainEngine.getBlockQueries().getBestHeight().get() + 1
    ).also {
        it.recomputeStatus()
    }

    private val blockDatabase = BaseBlockDatabase(
        blockchainEngine, blockchainEngine.getBlockQueries(), workerContext.nodeId
    )

    // Give the SyncManager the BaseTransactionQueue (part of workerContext) and not the network-aware one,
    // because we don't want tx forwarding/broadcasting when received through p2p network
    val syncManager = ValidatorSyncManager(
        workerContext,
        statusManager,
        BaseBlockManager(
            workerContext.processName,
            blockDatabase,
            statusManager,
            blockchainEngine.getBlockBuildingStrategy()
        ),
        blockDatabase,
        nodeStateTracker
    )

    val networkAwareTxQueue = NetworkAwareTxQueue(
        blockchainEngine.getTransactionQueue(),
        workerContext.communicationManager
    )

    fun isInFastSyncMode(): Boolean {
        return syncManager.isInFastSync()
    }

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
}