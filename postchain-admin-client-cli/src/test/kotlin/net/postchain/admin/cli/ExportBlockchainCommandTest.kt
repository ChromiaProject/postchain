package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.base.importexport.ExportResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ExportBlockchainCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val configurationsFile = "configurationsFile"
    private val blocksFile = "blocksFile"
    private val fromHeight = 10L
    private val upToHeight = 20L
    private val blocks = 10L

    private val exportResult = ExportResult(fromHeight, upToHeight, blocks)

    private lateinit var command: ExportBlockchainCommand

    @BeforeEach
    fun beforeEach() {
        doReturn(exportResult).whenever(postchainService).exportBlockchain(anyLong(), anyOrNull(), anyOrNull(), anyBoolean(), anyLong(), anyLong())
        command = ExportBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Export with range and block file`() {
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile,
                        "--blocks-file", blocksFile,
                        "--from-height", fromHeight.toString(),
                        "--up-to-height", upToHeight.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).exportBlockchain(chainId, Path.of(configurationsFile), Path.of(blocksFile), false, fromHeight, upToHeight)
        testConsole.assertContains("Export of 10 blocks 10..20 to configurationsFile and blocksFile completed\n")
    }

    @Test
    fun `Export with range without block file`() {
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile,
                        "--from-height", fromHeight.toString(),
                        "--up-to-height", upToHeight.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).exportBlockchain(chainId, Path.of(configurationsFile), null, false, fromHeight, upToHeight)
        testConsole.assertContains("Export of configurations to configurationsFile completed\n")
    }

    @Test
    fun `Export without block file and without range`() {
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).exportBlockchain(chainId, Path.of(configurationsFile), null, false, 0L, Long.MAX_VALUE)
        testConsole.assertContains("Export of configurations to configurationsFile completed\n")
    }

    @Test
    fun `Export with block file and without range`() {
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile,
                        "--blocks-file", blocksFile,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).exportBlockchain(chainId, Path.of(configurationsFile), Path.of(blocksFile), false, 0L, Long.MAX_VALUE)
        testConsole.assertContains("Export of 10 blocks 10..20 to configurationsFile and blocksFile completed\n")
    }
}