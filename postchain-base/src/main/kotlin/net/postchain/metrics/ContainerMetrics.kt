package net.postchain.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import java.io.Closeable

class ContainerMetrics(containerBlockchainProcessManager: ContainerManagedBlockchainProcessManager) : Closeable {
    private val subnodesGauge: Meter = Gauge.builder("subnodes", containerBlockchainProcessManager) { containerBlockchainProcessManager.numberOfSubnodes().toDouble() }
            .description("Number of subnodes which should be running")
            .register(Metrics.globalRegistry)

    private val containersGauge: Meter = Gauge.builder("containers", containerBlockchainProcessManager) { containerBlockchainProcessManager.numberOfContainers().toDouble() }
            .description("Number of containers which should be running")
            .register(Metrics.globalRegistry)

    override fun close() {
        Metrics.globalRegistry.remove(containersGauge)
        Metrics.globalRegistry.remove(subnodesGauge)
    }
}
