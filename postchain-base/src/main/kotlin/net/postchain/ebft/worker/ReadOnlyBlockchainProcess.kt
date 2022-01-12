// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer

class ReadOnlyBlockchainProcess(val workerContext: WorkerContext) : AbstractBlockchainProcess(workerContext.processName.toString()) {

    companion object : KLogging()

    override fun getEngine() = workerContext.engine

    private val fastSynchronizer: FastSynchronizer

    private val blockDatabase = BaseBlockDatabase(
            getEngine(), getEngine().getBlockQueries(), NODE_ID_READ_ONLY)

    init {
        val params = FastSyncParameters(jobTimeout = workerContext.nodeConfig.fastSyncJobTimeout)

        fastSynchronizer = FastSynchronizer(workerContext,
                blockDatabase,
                params
        )

        startProcess()
    }

    override fun action() {
        fastSynchronizer.syncUntil { isShuttingDown() }
    }

    fun getHeight(): Long = fastSynchronizer.blockHeight

    override fun shutdown() {
        shutdownDebug("Begin")
        fastSynchronizer.shutdown()
        blockDatabase.stop()
        super.shutdown()
        workerContext.shutdown()
        shutdownDebug("End")
    }

    private fun shutdownDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("${workerContext.processName}: shutdown() - $str.")
        }
    }
}