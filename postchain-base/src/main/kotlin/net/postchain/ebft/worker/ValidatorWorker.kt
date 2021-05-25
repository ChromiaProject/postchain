// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import net.postchain.base.NetworkAwareTxQueue
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.NodeStateTracker
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.StatusManager
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.syncmanager.SyncManager
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A blockchain instance worker
 *
 * @param workerContext The stuff needed to start working.
 */
class ValidatorWorker(private val workerContext: WorkerContext) : BlockchainProcess {

    private var heartbeat: HeartbeatEvent? = null
    private lateinit var updateLoop: Thread
    private val shutdown = AtomicBoolean(false)

    private val blockDatabase: BaseBlockDatabase
    private val blockManager: BaseBlockManager
    val syncManager: ValidatorSyncManager
    val networkAwareTxQueue: NetworkAwareTxQueue
    val nodeStateTracker = NodeStateTracker()
    val statusManager: StatusManager

    init {
        val bestHeight = getEngine().getBlockQueries().getBestHeight().get()
        statusManager = BaseStatusManager(
                workerContext.signers.size,
                workerContext.nodeId,
                bestHeight + 1)

        blockDatabase = BaseBlockDatabase(
                getEngine(), getEngine().getBlockQueries(), workerContext.nodeId)

        blockManager = BaseBlockManager(
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

        workerContext.communicationManager.setHeartbeatListener(this)

        startUpdateLoop(syncManager)
    }

    /**
     * Create and run the [updateLoop] thread
     * @param syncManager the syncronization manager
     */
    protected fun startUpdateLoop(syncManager: SyncManager) {
        updateLoop = thread(name = "updateLoop-${workerContext.processName}") {
            while (!shutdown.get()) {
                try {
                    if (checkHeartbeat()) {
                        syncManager.update()
                        Thread.sleep(20)
                    } else {
                        Thread.sleep(workerContext.nodeConfig.heartbeatSleepTimeout)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun getEngine(): BlockchainEngine = workerContext.engine

    /**
     * Stop the postchain node
     */
    override fun shutdown() {
        syncManager.shutdown()
        shutdown.set(true)
        updateLoop.join()
        blockDatabase.stop()
        workerContext.shutdown()
    }

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        heartbeat = heartbeatEvent
        // Pass a heartbeat event to SyncManager to check heartbeat for
        // each network message because communicationManager.getPackets()
        // might give a big portion of messages (see syncManager.dispatchMessages()).
        // SyncManager is internal HeartbeatListener (not registered at HeartbeatManager).
        syncManager.onHeartbeat(heartbeatEvent)
    }

    // 1. Heartbeat check is failed if there is no registered heartbeat event.
    // 2. The field blockManager.lastBlockTimestamp will be set to non-null value
    // after the first block db operation. So we read lastBlockTimestamp value from db
    // until blockManager.lastBlockTimestamp is non-null.
    override fun checkHeartbeat(): Boolean {
        if (!workerContext.nodeConfig.heartbeat) return true
        if (heartbeat == null) return false
        val lastBlockTimestamp = blockManager.lastBlockTimestamp
                ?: getEngine().getBlockQueries().getLastBlockTimestamp().get()
        return lastBlockTimestamp - heartbeat!!.timestamp < workerContext.nodeConfig.heartbeatTimeout
    }
}