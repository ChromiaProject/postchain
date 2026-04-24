package net.postchain.api.rest.infra

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.postchain.containers.api.DefaultMasterApiInfra
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultMasterApiInfraTest {

    @Test
    fun `defaults on typical production host split total into local and external`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 16,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 0,
                configuredLocal = 0,
                configuredExternal = 0,
                configuredContainer = 0,
        )
        assertThat(r.total).isEqualTo(32)
        assertThat(r.local).isEqualTo(10)
        assertThat(r.external).isEqualTo(22)
        assertThat(r.container).isEqualTo(5) // max(1, 22 / 4)
    }

    @Test
    fun `defaults on low-CPU host still leave at least one permit for external`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 4,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 0,
                configuredLocal = 0,
                configuredExternal = 0,
                configuredContainer = 0,
        )
        assertThat(r.total).isEqualTo(8)
        assertThat(r.local).isEqualTo(7)
        assertThat(r.external).isEqualTo(1)
        assertThat(r.container).isEqualTo(1)
    }

    @Test
    fun `defaults on single-CPU host do not produce zero or negative pools`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 1,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 0,
                configuredLocal = 0,
                configuredExternal = 0,
                configuredContainer = 0,
        )
        assertThat(r.total).isEqualTo(2)
        assertThat(r.local).isEqualTo(1)
        assertThat(r.external).isEqualTo(1)
        assertThat(r.container).isEqualTo(1)
    }

    @Test
    fun `setting only the total derives local and external from CPU and DB pool`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 4,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 20,
                configuredLocal = 0,
                configuredExternal = 0,
                configuredContainer = 0,
        )
        assertThat(r.total).isEqualTo(20)
        // min(10, 8) = 8, capped at 19 → 8
        assertThat(r.local).isEqualTo(8)
        assertThat(r.external).isEqualTo(12)
        assertThat(r.container).isEqualTo(3) // 12 / 4
    }

    @Test
    fun `explicit per-pool values pass through without recomputation`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 16,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 50,
                configuredLocal = 5,
                configuredExternal = 10,
                configuredContainer = 3,
        )
        assertThat(r.total).isEqualTo(50)
        assertThat(r.local).isEqualTo(5)
        assertThat(r.external).isEqualTo(10)
        assertThat(r.container).isEqualTo(3)
    }

    @Test
    fun `disabling a pool with -1 passes through`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 16,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 0,
                configuredLocal = 0,
                configuredExternal = -1,
                configuredContainer = -1,
        )
        assertThat(r.total).isEqualTo(32)
        assertThat(r.local).isEqualTo(10)
        assertThat(r.external).isEqualTo(-1)
        assertThat(r.container).isEqualTo(-1)
    }

    @Test
    fun `operator setting local equal to total fails because external would be zero`() {
        val exception = assertThrows<IllegalArgumentException> {
            DefaultMasterApiInfra.resolveConcurrency(
                    availableProcessors = 16,
                    databaseSharedReadConcurrency = 10,
                    configuredTotal = 10,
                    configuredLocal = 10,
                    configuredExternal = 0,
                    configuredContainer = 0,
            )
        }
        assertThat(exception.message!!).contains("api.request-concurrency.external is invalid (0)")
    }

    @Test
    fun `operator setting local greater than total fails with a negative external`() {
        val exception = assertThrows<IllegalArgumentException> {
            DefaultMasterApiInfra.resolveConcurrency(
                    availableProcessors = 16,
                    databaseSharedReadConcurrency = 10,
                    configuredTotal = 10,
                    configuredLocal = 20,
                    configuredExternal = 0,
                    configuredContainer = 0,
            )
        }
        assertThat(exception.message!!).contains("api.request-concurrency.external is invalid (-10)")
    }

    @Test
    fun `container auto-calc falls back to total when external is disabled`() {
        val r = DefaultMasterApiInfra.resolveConcurrency(
                availableProcessors = 16,
                databaseSharedReadConcurrency = 10,
                configuredTotal = 0,
                configuredLocal = 0,
                configuredExternal = -1,
                configuredContainer = 0,
        )
        // external is -1 (disabled) so container is derived from total = 32
        assertThat(r.container).isEqualTo(8) // 32 / 4
    }
}