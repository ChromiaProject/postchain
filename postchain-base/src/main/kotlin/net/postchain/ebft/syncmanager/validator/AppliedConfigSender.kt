package net.postchain.ebft.syncmanager.validator

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.core.Shutdownable
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.worker.WorkerContext
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.network.PacketVersionFilter
import net.postchain.network.common.LazyPacket
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AppliedConfigSender(
        private val workerContext: WorkerContext,
        private val interval: Duration,
        private val currentHeightProvider: () -> Long
) : Shutdownable {

    var isStarted = false
        private set

    private val configHash = workerContext.blockchainConfiguration.configHash
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder().setNameFormat("AppliedConfigSender-c${workerContext.blockchainConfiguration.chainID}").build())
    private var previousMessage: Pair<AppliedConfig, Map<Long, LazyPacket>>? = null

    companion object : KLogging()

    fun start() {
        executor.scheduleWithFixedDelay(::sendAppliedConfig, 0, interval.toMillis(), TimeUnit.MILLISECONDS)
        logger.info { "AppliedConfigSender started" }
        isStarted = true
    }

    private fun sendAppliedConfig() {
        withLoggingContext(
                BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex(),
                CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString()
        ) {
            try {
                val nextHeight = currentHeightProvider() + 1
                val appliedConfigMessage = AppliedConfig(configHash, nextHeight)

                previousMessage = previousMessage
                        ?.takeIf { it.first.height == nextHeight }
                        ?.apply {
                            workerContext.communicationManager.broadcastPacket(appliedConfigMessage, this.second, versionFilter())
                        }
                        ?: (appliedConfigMessage to workerContext.communicationManager.broadcastPacket(appliedConfigMessage, null, versionFilter()))
            } catch (e: Exception) {
                logger.error("Unexpected error while sending applied config", e)
            }
        }
    }

    // From version 2, the config hash is included in the Status message instead.
    private fun versionFilter(): PacketVersionFilter = { it < 2 }

    override fun shutdown() {
        executor.shutdownNow()
        executor.awaitTermination(2000, TimeUnit.MILLISECONDS)
    }
}