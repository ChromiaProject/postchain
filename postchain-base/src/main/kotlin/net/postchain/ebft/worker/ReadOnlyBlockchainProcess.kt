// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer

class ReadOnlyBlockchainProcess(val workerContext: WorkerContext) : AbstractBlockchainProcess("replica-${workerContext.processName}", workerContext.engine) {

    companion object : KLogging()

    private val blockDatabase = BaseBlockDatabase(
            blockchainEngine, blockchainEngine.getBlockQueries(), NODE_ID_READ_ONLY)

    private val fastSynchronizer = FastSynchronizer(
            workerContext,
            blockDatabase,
            FastSyncParameters(jobTimeout = workerContext.nodeConfig.fastSyncJobTimeout),
            ::isProcessRunning
    )

    override fun action() {
        fastSynchronizer.syncUntil { !isProcessRunning() }
    }

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        workerContext.heartbeatChecker.onHeartbeat(heartbeatEvent)
    }

    fun getHeight(): Long = fastSynchronizer.blockHeight

    override fun cleanup() {
        blockDatabase.stop()
        workerContext.shutdown()
    }
}
