package net.postchain.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Metrics

class BlockchainProcessManagerMetrics(numberOfBlockchains: () -> Int) {
    init {
        Gauge.builder("blockchains", numberOfBlockchains) { numberOfBlockchains().toDouble() }
                .description("Number of blockchains running")
                .register(Metrics.globalRegistry)
    }
}
