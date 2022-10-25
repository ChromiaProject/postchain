package net.postchain.containers

import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.ContainerResourceLimits.ResourceLimitType.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContainerResourceLimitsTest {

    @Test
    fun testDefaultValues() {
        val sut = ContainerResourceLimits.default()
        assertFalse { sut.hasCpu() }
        assertFalse { sut.hasRam() }
        assertFalse { sut.hasStorage() }
    }

    @Test
    fun testZeroValues() {
        val sut = ContainerResourceLimits.fromValues(0, 0, 0)
        assertFalse { sut.hasCpu() }
        assertFalse { sut.hasRam() }
        assertFalse { sut.hasStorage() }
    }

    @ParameterizedTest(name = "[{index}] cpu: {0}, expectedQuota: {1}")
    @CsvSource(
            "1, 1_000",         // 1%
            "20, 20_000",       // 20%
            "100, 100_000",     // 100%
            "320, 320_000",     // 320% == 3.2 cpus
    )
    fun testCpu(cpu: Long, expectedQuota: Long) {
        val sut = ContainerResourceLimits(CPU to cpu)
        assertTrue { sut.hasCpu() }
        assertEquals(100_000L, sut.cpuPeriod())
        assertEquals(expectedQuota, sut.cpuQuota())
    }

    @Test
    fun testRam() {
        val sut = ContainerResourceLimits(RAM to 10)
        assertTrue { sut.hasRam() }
        assertEquals(10 * 1024 * 1024L, sut.ramBytes())
    }

    @Test
    fun testStorage() {
        val sut = ContainerResourceLimits(STORAGE to 123)
        assertTrue { sut.hasStorage() }
        assertEquals(123L, sut.storageMb())
    }
}