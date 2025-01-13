package net.postchain.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.TimeGauge
import mu.KLogging
import net.postchain.containers.bpm.ContainerResourceUsage
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty1


const val SUB_CONTAINER_METRICS_MEMORY_USAGE = "sub_container,memory_usage"
const val SUB_CONTAINER_METRICS_MEMORY_USAGE_PERCENTAGE = "sub_container,memory_usage_percentage"
const val SUB_CONTAINER_METRICS_CPU_USAGE_PERCENTAGE = "sub_container,cpu_usage_percentage"
const val SUB_CONTAINER_METRICS_SPACE_USAGE_MIB = "sub_container,space_usage_mib"
const val SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE = "sub_container,space_usage_percentage"
const val SUB_CONTAINER_METRICS_SPACE_LEFT_MB = "sub_container,space_left_mb"
const val SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME = "sub_container,space_update_time"
const val SUB_CONTAINER_CONTAINER_NAME_TAG = "containerName"

/**
 * For one container this registers, updates and unregister metrics related to container resource usage.
 */
class SubContainerResourceMetrics(
        private val directoryContainer: String,
        private val enableSpaceMetrics: Boolean,
        refreshInterval: Long,
        private val spaceRefreshInterval: Long,
        private val getContainerResourceUsage: (includeSpaceUsage: Boolean) -> ContainerResourceUsage?
) : Closeable {

    companion object : KLogging() {

        /**
         * Get metric value for a specific container, or null.
         */
        fun getContainerMetricValue(containerName: String, metricName: String): Double? {

            val metric = Metrics.globalRegistry.meters.find { metric ->
                metric.id.name == metricName && metric.id.tags.any { tag ->
                    tag.key == SUB_CONTAINER_CONTAINER_NAME_TAG && tag.value == containerName
                }
            }

            return metric?.measure()?.iterator()?.next()?.value
        }

        // Defined available metrics
        private val metricDefinitions = listOf(
                SubContainerResourceMetricData(SUB_CONTAINER_METRICS_MEMORY_USAGE, "Memory usage in bytes", ContainerResourceUsage::memoryUsage),
                SubContainerResourceMetricData(SUB_CONTAINER_METRICS_MEMORY_USAGE_PERCENTAGE, "Memory usage in percent", ContainerResourceUsage::memoryUsagePercentage),
                SubContainerResourceMetricData(SUB_CONTAINER_METRICS_CPU_USAGE_PERCENTAGE, "CPU usage in percent", ContainerResourceUsage::cpuUsagePercentage),
                SubContainerResourceMetricData(SUB_CONTAINER_METRICS_SPACE_USAGE_MIB, "Space usage in MiB", ContainerResourceUsage::spaceUsageMiB, true),
                SubContainerResourceMetricData(SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE, "Space usage in percent", ContainerResourceUsage::spaceUsagePercentage, true),
                SubContainerResourceMetricData(SUB_CONTAINER_METRICS_SPACE_LEFT_MB, "Space left in MB", ContainerResourceUsage::spaceLeftMib, true),
        )
    }

    private var metrics = mutableListOf<Meter>()
    private var lastResourceUsage: ContainerResourceUsage? = null
    private var lastSpaceCheckTime: Instant? = null
    private val scheduledExecutorService = Executors.newScheduledThreadPool(100, Thread.ofVirtual().factory())

    init {
        metricDefinitions
                .filter { enableSpaceMetrics || !it.isSpaceMetric }
                .forEach(this::gaugeMetric)

        TimeGauge.builder(SUB_CONTAINER_METRICS_SPACE_UPDATE_TIME, {
            if (lastSpaceCheckTime != null) {
                lastSpaceCheckTime!!.toEpochMilli()
            } else {
                Double.NaN
            }
        }, TimeUnit.MILLISECONDS)
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, directoryContainer)
                .register(Metrics.globalRegistry)
                .apply {
                    metrics.add(this)
                }

        scheduledExecutorService.scheduleWithFixedDelay(this::updateMetrics, 0, refreshInterval, TimeUnit.MILLISECONDS)
    }

    private fun updateMetrics() {
        try {
            val includeSpaceUsage = checkSpaceUsage()

            val start = System.currentTimeMillis()
            val resourceUsage = getContainerResourceUsage(includeSpaceUsage)
            if (resourceUsage != null) {
                if (!includeSpaceUsage && lastResourceUsage != null) {
                    resourceUsage.copySpacePropertiesFrom(lastResourceUsage!!)
                }
                lastResourceUsage = resourceUsage
            }

            logger.debug { "Fetching resource usage for container $directoryContainer took ${System.currentTimeMillis() - start} ms, with space check: $includeSpaceUsage" }
        } catch (e: Exception) {
            logger.error { "Failed to update container resource metrics for container $directoryContainer: ${e.message}" }
        }
    }

    private fun checkSpaceUsage(): Boolean {
        if (enableSpaceMetrics && (lastSpaceCheckTime ?: Instant.MIN)
                        .isBefore(Instant.now().minusMillis(spaceRefreshInterval))) {
            lastSpaceCheckTime = Instant.now()
            return true
        }
        return false
    }

    private fun gaugeMetric(metricSpec: SubContainerResourceMetricData): Gauge {
        return Gauge.builder(metricSpec.name) {
            if (lastResourceUsage != null) {
                metricSpec.property.get(lastResourceUsage!!) ?: Double.NaN
            } else {
                Double.NaN
            }
        }
                .tags(SUB_CONTAINER_CONTAINER_NAME_TAG, directoryContainer)
                .register(Metrics.globalRegistry)
                .apply {
                    metrics.add(this)
                }
    }

    override fun close() {
        scheduledExecutorService.shutdownNow()
        metrics.forEach(Metrics.globalRegistry::remove)
    }
}

/**
 * Keeps a container resource metric definition
 */
data class SubContainerResourceMetricData(
        val name: String,
        val description: String,
        val property: KProperty1<ContainerResourceUsage, Number?>,
        val isSpaceMetric: Boolean = false,
)
