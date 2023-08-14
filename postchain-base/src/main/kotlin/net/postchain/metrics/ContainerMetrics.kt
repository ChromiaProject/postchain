package net.postchain.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Metrics

class ContainerMetrics(numberOfSubnodes: () -> Int, numberOfContainers: () -> Int) {
    init {
        Gauge.builder("subnodes", numberOfSubnodes) { numberOfSubnodes().toDouble() }
                .description("Number of subnodes which should be running")
                .register(Metrics.globalRegistry)

        Gauge.builder("containers", numberOfContainers) { numberOfContainers().toDouble() }
                .description("Number of containers which should be running")
                .register(Metrics.globalRegistry)
    }
}
