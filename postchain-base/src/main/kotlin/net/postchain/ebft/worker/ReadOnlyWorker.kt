// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import net.postchain.core.BlockchainProcess
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.heartbeat.HeartbeatEvent
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class ReadOnlyWorker(private val workerContext: WorkerContext) : BlockchainProcess {

    private val fastSynchronizer: FastSynchronizer
    private val done = CountDownLatch(1)

    init {
        val blockDatabase = BaseBlockDatabase(
                getEngine(), getEngine().getBlockQueries(), NODE_ID_READ_ONLY)

        val params = FastSyncParameters()
        params.jobTimeout = workerContext.nodeConfig.fastSyncJobTimeout

        fastSynchronizer = FastSynchronizer(workerContext, blockDatabase, params)
        workerContext.communicationManager.setHeartbeatListener(this)
        thread(name = "replicaSync-${workerContext.processName}") {
            fastSynchronizer.syncUntilShutdown()
            done.countDown()
        }
    }

    override fun getEngine() = workerContext.engine

    override fun shutdown() {
        fastSynchronizer.shutdown()
        done.await()
        workerContext.shutdown()
    }

    override fun onHeartbeat(heartbeatEvent: HeartbeatEvent) {
        workerContext.heartbeatChecker.onHeartbeat(heartbeatEvent)
    }
}