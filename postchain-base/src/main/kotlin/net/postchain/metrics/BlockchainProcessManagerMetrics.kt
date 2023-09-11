package net.postchain.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import net.postchain.base.BaseBlockchainProcessManager
import java.io.Closeable

class BlockchainProcessManagerMetrics(blockchainProcessManager: BaseBlockchainProcessManager) : Closeable {
    private val blockchainsGauge: Meter =
            Gauge.builder("blockchains", blockchainProcessManager) { blockchainProcessManager.numberOfBlockchains().toDouble() }
                    .description("Number of blockchains running")
                    .register(Metrics.globalRegistry)

    override fun close() {
        Metrics.globalRegistry.remove(blockchainsGauge)
    }
}
