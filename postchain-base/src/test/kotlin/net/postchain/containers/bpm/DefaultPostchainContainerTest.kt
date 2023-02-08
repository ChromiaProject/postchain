package net.postchain.containers.bpm

import net.postchain.containers.bpm.resources.Cpu
import net.postchain.containers.bpm.resources.Ram
import net.postchain.containers.bpm.resources.Storage
import net.postchain.managed.DirectoryDataSource
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class DefaultPostchainContainerTest {

    @Test
    fun testCheckForResourceLimitsUpdates() {
        // setup
        val limits1 = ContainerResourceLimits(Cpu(1), Ram(123), Storage(456))
        val limits2 = ContainerResourceLimits(Cpu(1), Ram(123), Storage(4560))

        // mocks
        val containerName: ContainerName = mock {
            on { directoryContainer } doReturn "my_container"
        }
        val dataSource: DirectoryDataSource = mock {
            on { getResourceLimitForContainer(any()) } doReturnConsecutively listOf(limits1, limits1, limits2)
        }

        // sut
        val sut = DefaultPostchainContainer(dataSource, containerName, mock(), mock(), mock())

        // interaction(s)
        // 1. no updates
        val updates = sut.checkForResourceLimitsUpdates()
        assertEquals(false, updates.first)
        assertEquals(null, updates.second)

        // 2. updates available
        val updates2 = sut.checkForResourceLimitsUpdates()
        assertEquals(true, updates2.first)
        assertEquals(limits2, updates2.second)
    }
}