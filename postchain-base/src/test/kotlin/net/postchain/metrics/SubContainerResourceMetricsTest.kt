package net.postchain.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.postchain.containers.bpm.ContainerResourceUsage
import net.postchain.metrics.SubContainerResourceMetrics.Companion.getContainerMetricValue
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


class SubContainerResourceMetricsTest {

    @Test
    fun `no metrics available`() {

            assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_MEMORY_USAGE)).isNull()
            assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_MEMORY_USAGE_PERCENTAGE)).isNull()
            assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_CPU_USAGE_PERCENTAGE)).isNull()
            assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_SPACE_USAGE_MIB)).isNull()
            assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE)).isNull()
    }

    @Test
    fun `report and read all metrics`() {

        val metrics = SubContainerResourceMetrics("container-1", true, Long.MAX_VALUE, 0) {
            ContainerResourceUsage(1, 2, 3.0, 4.0, 5, 6, 7.0)
        }

        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_MEMORY_USAGE)).isEqualTo(1.0)
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_MEMORY_USAGE_PERCENTAGE)).isEqualTo(3.0)
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_CPU_USAGE_PERCENTAGE)).isEqualTo(4.0)
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_SPACE_USAGE_MIB)).isEqualTo(5.0)
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_SPACE_USAGE_PERCENTAGE)).isEqualTo(7.0)
                }

        metrics.close()
    }

    @Test
    fun `multiple container metrics`() {

        val metrics1 = SubContainerResourceMetrics("container-1", true, Long.MAX_VALUE, 0) {
            ContainerResourceUsage(1)
        }
        val metrics2 = SubContainerResourceMetrics("container-2", true, Long.MAX_VALUE, 0) {
            ContainerResourceUsage(2)
        }

        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_MEMORY_USAGE)).isEqualTo(1.0)
                    assertThat(getContainerMetricValue("container-2", SUB_CONTAINER_METRICS_MEMORY_USAGE)).isEqualTo(2.0)
                }

        metrics1.close()
        metrics2.close()
    }

    @Test
    fun `no space metrics`() {

        val metrics = SubContainerResourceMetrics("container-1", false, Long.MAX_VALUE, 0) {
            ContainerResourceUsage(memoryUsage = 1, spaceUsageMiB = 2)
        }

        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_MEMORY_USAGE)).isEqualTo(1.0)
                    assertThat(getContainerMetricValue("container-1", SUB_CONTAINER_METRICS_SPACE_USAGE_MIB)).isNull()
                }

        metrics.close()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            Metrics.globalRegistry.add(prometheusRegistry)
        }
    }
}
