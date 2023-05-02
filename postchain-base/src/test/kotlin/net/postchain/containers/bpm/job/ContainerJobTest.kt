package net.postchain.containers.bpm.job

import net.postchain.containers.bpm.ContainerName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class ContainerJobTest {

    @Test
    fun verifyBackoffTimes() {
        val currentTime = 42L
        val containerName = mock<ContainerName>()
        whenever(containerName.name).thenReturn("Name")
        val job = object : ContainerJob(containerName) {
            override fun currentTimeMillis() = currentTime
        }
        assertEquals(0, job.failedStartCount)
        assertEquals(0, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(1, job.failedStartCount)
        assertEquals(1000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(2, job.failedStartCount)
        assertEquals(2000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(3, job.failedStartCount)
        assertEquals(4000 + 42, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(4, job.failedStartCount)
        assertEquals(8000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(5, job.failedStartCount)
        assertEquals(16000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(6, job.failedStartCount)
        assertEquals(32000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(7, job.failedStartCount)
        assertEquals(64000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(8, job.failedStartCount)
        assertEquals(128000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(9, job.failedStartCount)
        assertEquals(256000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(9, job.failedStartCount)
        assertEquals(300000 + currentTime, job.nextExecutionTime)
        job.postponeWithBackoff()
        assertEquals(9, job.failedStartCount)
        assertEquals(300000 + currentTime, job.nextExecutionTime)

        job.resetFailedStartCount()
        job.postponeWithBackoff()
        assertEquals(1, job.failedStartCount)
        assertEquals(1000 + currentTime, job.nextExecutionTime)
    }
}