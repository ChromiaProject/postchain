package net.postchain.containers.bpm.fs

import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.infra.ContainerNodeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyArray
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class Ext4FileSystemTest {

    private val realResult: List<String> = listOf(
            "Project,SpaceStatus,FileStatus,SpaceUsed,SpaceSoftLimit,SpaceHardLimit,SpaceGrace,FileUsed,FileSoftLimit,FileHardLimit,FileGrace",
            "#0,ok,ok,1M,0M,0M,,1k,0k,0k,",
            "#1,ok,ok,51M,0M,16384M,,2k,0k,0k,")
    private val containerConfig: ContainerNodeConfig = mock()
    private val containerName: ContainerName = mock()
    private val resourceLimits: ContainerResourceLimits = mock()
    private lateinit var sut: Ext4FileSystem

    @BeforeEach
    fun beforeTest() {
        sut = spy(Ext4FileSystem(containerConfig))
        doReturn(FileSystem.CommandResult(0, realResult)).whenever(sut).runCommandWithOutput(anyArray())
        whenever(containerName.containerIID).thenReturn(1)
        whenever(resourceLimits.hasStorage()).thenReturn(true)
    }

    @Test
    fun `getCurrentLimitsInfo with valid repquota command result should be parsed correctly`() {
        // execute
        val result = sut.getCurrentLimitsInfo(containerName, resourceLimits)
        // verify
        checkNotNull(result)
        assertEquals(51L, result.spaceUsedMB)
        assertEquals(16384L, result.spaceHardLimitMB)
    }

    @Test
    fun `getCurrentLimitsInfo with missing project should return null`() {
        // setup
        whenever(containerName.containerIID).thenReturn(42)
        // execute & verify
        assertNull(sut.getCurrentLimitsInfo(containerName, resourceLimits))
    }

    @Test
    fun `getCurrentLimitsInfo with missing storage resource should return null`() {
        // setup
        whenever(resourceLimits.hasStorage()).thenReturn(false)
        // execute & verify
        assertNull(sut.getCurrentLimitsInfo(containerName, resourceLimits))
    }
}