package net.postchain.metrics

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.HTTPServer
import net.postchain.PostchainNode
import net.postchain.config.app.AppConfig
import net.postchain.logging.NODE_PUBKEY_TAG
import java.net.InetSocketAddress

fun initMetrics(appConfig: AppConfig) {
    val registry = Metrics.globalRegistry
    registry.config().commonTags(listOf(Tag.of(NODE_PUBKEY_TAG, appConfig.pubKey)))

    ClassLoaderMetrics().bindTo(registry)
    JvmMemoryMetrics().bindTo(registry)
    JvmGcMetrics().bindTo(registry)
    JvmThreadMetrics().bindTo(registry)
    ProcessorMetrics().bindTo(registry)
    UptimeMetrics().bindTo(registry)

    val prometheusPort = appConfig.getEnvOrInt("POSTCHAIN_PROMETHEUS_PORT", "metrics.prometheus.port", -1)
    if (prometheusPort > 0) {
        initPrometheus(registry, prometheusPort)
    } else {
        registry.add(SimpleMeterRegistry())
    }
}

private fun initPrometheus(registry: CompositeMeterRegistry, port: Int) {
    registry.config().meterFilter(object : MeterFilter {
        override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig {
            return DistributionStatisticConfig.builder()
                    .percentiles(0.9, 0.95, 0.99)
                    .build()
                    .merge(config)
        }
    })
    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    registry.add(prometheusRegistry)
    try {
        HTTPServer(InetSocketAddress(port), prometheusRegistry.prometheusRegistry, true)
        PostchainNode.logger.info("Exposing Prometheus metrics on port $port")
    } catch (e: Exception) {
        PostchainNode.logger.error(e) { "Error when starting Prometheus metrics on $port" }
    }
}
