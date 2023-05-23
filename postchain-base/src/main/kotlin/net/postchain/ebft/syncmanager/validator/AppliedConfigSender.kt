package net.postchain.ebft.syncmanager.validator

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.postchain.concurrent.util.get
import net.postchain.core.Shutdownable
import net.postchain.core.block.BlockQueries
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.CommunicationManager
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AppliedConfigSender(
        private val configHash: ByteArray,
        private val blockQueries: BlockQueries,
        private val communicationManager: CommunicationManager<EbftMessage>,
        interval: Duration
) : Shutdownable {

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder().setNameFormat("AppliedConfigSender").build())

    init {
        executor.scheduleAtFixedRate(::sendAppliedConfig, 0, interval.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun sendAppliedConfig() {
        val currentHeight = blockQueries.getLastBlockHeight().get() + 1
        communicationManager.broadcastPacket(AppliedConfig(configHash, currentHeight))
    }

    override fun shutdown() {
        executor.shutdownNow()
        executor.awaitTermination(2000, TimeUnit.MILLISECONDS)
    }
}