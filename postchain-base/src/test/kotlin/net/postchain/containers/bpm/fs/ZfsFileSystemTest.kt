package net.postchain.containers.bpm.fs

import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.command.CommandExecutor
import net.postchain.containers.bpm.command.CommandResult
import net.postchain.containers.infra.ContainerNodeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyArray
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ZfsFileSystemTest {

    private val realResult: List<String> = listOf("used\t16163180032", "quota\t17179869184")
    private val contName = "c1"
    private val zfsPool = "pool1"

    private val containerConfig: ContainerNodeConfig = mock {
        on { zfsPoolName } doReturn zfsPool
    }
    private val containerName: ContainerName = mock {
        on { name } doReturn contName
    }
    private val resourceLimits: ContainerResourceLimits = mock {
        on { hasStorage() } doReturn true
    }
    private val commandExecutor: CommandExecutor = mock()
    private val sut = ZfsFileSystem(containerConfig, commandExecutor)

    @BeforeEach
    fun beforeTest() {
        doReturn(CommandResult(0, realResult, listOf())).whenever(commandExecutor).runCommandWithOutput(anyArray())
    }

    @Test
    fun `getCurrentLimitsInfo with valid list command result should be parsed correctly`() {
        // execute
        val result = sut.getCurrentLimitsInfo(containerName, resourceLimits)
        // verify
        checkNotNull(result)
        assertEquals(15414L, result.spaceUsedMB)
        assertEquals(16384L, result.spaceHardLimitMB)
    }

    @Test
    fun `getCurrentLimitsInfo with missing project should return null`() {
        // setup
        whenever(containerName.name).thenReturn("foo")
        doReturn(CommandResult(1, listOf(), listOf("cannot open '/pool1/foo': No such file or directory"))).whenever(commandExecutor).runCommandWithOutput(anyArray())
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