package net.postchain.containers.bpm.fs

import net.postchain.common.exception.UserMistake
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.command.CommandExecutor
import net.postchain.containers.bpm.command.CommandResult
import net.postchain.containers.infra.ContainerNodeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class Ext4FileSystemTest {

    private val masterMountDir = "/data/chromaway/subnodes"
    private val quotaOnResult = listOf("project quota on $masterMountDir (/dev/vdb1) is on")
    private val limitsResult: List<String> = listOf(
            "Project,SpaceStatus,FileStatus,SpaceUsed,SpaceSoftLimit,SpaceHardLimit,SpaceGrace,FileUsed,FileSoftLimit,FileHardLimit,FileGrace",
            "#0,ok,ok,1M,0M,0M,,1k,0k,0k,",
            "#1,ok,ok,51M,0M,16384M,,2k,0k,0k,")
    private val containerConfig: ContainerNodeConfig = mock {
        on { masterMountDir } doReturn masterMountDir
    }
    private val containerName: ContainerName = mock()
    private val resourceLimits: ContainerResourceLimits = mock()
    private val commandExecutor: CommandExecutor = mock()

    @BeforeEach
    fun beforeTest() {
        doReturn(CommandResult(1, quotaOnResult, listOf())).whenever(commandExecutor).runCommandWithOutput(arrayOf("quotaon", "-p", "-P", masterMountDir))
        doReturn(CommandResult(0, limitsResult, listOf())).whenever(commandExecutor).runCommandWithOutput((arrayOf("repquota", "-P", "--human-readable=m,k", "-O", "csv", masterMountDir)))
        whenever(containerName.containerIID).thenReturn(1)
        whenever(resourceLimits.hasStorage()).thenReturn(true)
    }

    @Test
    fun `getCurrentLimitsInfo with valid repquota command result should be parsed correctly`() {
        val sut = Ext4FileSystem(containerConfig, commandExecutor)
        // execute
        val result = sut.getCurrentLimitsInfo(containerName, resourceLimits)
        // verify
        checkNotNull(result)
        assertEquals(51L, result.spaceUsedMB)
        assertEquals(16384L, result.spaceHardLimitMB)
    }

    @Test
    fun `getCurrentLimitsInfo with missing project should return null`() {
        val sut = Ext4FileSystem(containerConfig, commandExecutor)
        // setup
        whenever(containerName.containerIID).thenReturn(42)
        // execute & verify
        assertNull(sut.getCurrentLimitsInfo(containerName, resourceLimits))
    }

    @Test
    fun `getCurrentLimitsInfo with missing storage resource should return null`() {
        val sut = Ext4FileSystem(containerConfig, commandExecutor)
        // setup
        whenever(resourceLimits.hasStorage()).thenReturn(false)
        // execute & verify
        assertNull(sut.getCurrentLimitsInfo(containerName, resourceLimits))
    }

    @Test
    fun `Quota check fails due to quotas being turned off`() {
        val quotaOffResult = listOf("project quota on $masterMountDir (/dev/vdb1) is off")
        doReturn(CommandResult(0, quotaOffResult, listOf())).whenever(commandExecutor).runCommandWithOutput(arrayOf("quotaon", "-p", "-P", masterMountDir))

        assertThrows<UserMistake> {
            Ext4FileSystem(containerConfig, commandExecutor)
        }
    }

    @Test
    fun `Quota check fails due to quota not enabled`() {
        val quotaDisabledResult = listOf("quotaon: Mountpoint (or device) $masterMountDir not found or has no quota enabled.")
        doReturn(CommandResult(0, listOf(), quotaDisabledResult)).whenever(commandExecutor).runCommandWithOutput(arrayOf("quotaon", "-p", "-P", masterMountDir))

        assertThrows<UserMistake> {
            Ext4FileSystem(containerConfig, commandExecutor)
        }
    }

    @Test
    fun `Quota check fails since command fails to execute`() {
        doReturn(CommandResult(-1, listOf(), listOf())).whenever(commandExecutor).runCommandWithOutput(arrayOf("quotaon", "-p", "-P", masterMountDir))

        assertThrows<UserMistake> {
            Ext4FileSystem(containerConfig, commandExecutor)
        }
    }
}