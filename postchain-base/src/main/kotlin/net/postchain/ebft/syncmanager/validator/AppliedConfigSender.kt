package net.postchain.ebft.syncmanager.validator

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.withLoggingContext
import net.postchain.concurrent.util.get
import net.postchain.core.Shutdownable
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.worker.WorkerContext
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AppliedConfigSender(
        private val workerContext: WorkerContext,
        interval: Duration
) : Shutdownable {

    private val blockQueries = workerContext.engine.getBlockQueries()
    private val configHash = workerContext.blockchainConfiguration.configHash
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder().setNameFormat("AppliedConfigSender-c${workerContext.blockchainConfiguration.chainID}").build())

    init {
        executor.scheduleAtFixedRate(::sendAppliedConfig, 0, interval.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun sendAppliedConfig() {
        withLoggingContext(
                BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex(),
                CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString()
        ) {
            val currentHeight = blockQueries.getLastBlockHeight().get() + 1
            workerContext.communicationManager.broadcastPacket(AppliedConfig(configHash, currentHeight))
        }
    }

    override fun shutdown() {
        executor.shutdownNow()
        executor.awaitTermination(2000, TimeUnit.MILLISECONDS)
    }
}