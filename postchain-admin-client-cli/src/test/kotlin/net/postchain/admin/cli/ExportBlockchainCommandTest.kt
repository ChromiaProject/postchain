package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.base.importexport.ExportResult
import net.postchain.common.BlockchainRid
import org.http4k.base64DecodedArray
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
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val configurationsFile = "configurationsFile"
    private val blocksFile = "blocksFile"
    private val fromHeight = 10L
    private val upToHeight = 20L
    private val blocks = 10L

    private val exportResult = ExportResult(fromHeight, upToHeight, blocks)

    private lateinit var command: ExportBlockchainCommand

    @BeforeEach
    fun beforeEach() {
        doReturn(exportResult).whenever(postchainService).exportBlockchain(anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyBoolean(), anyLong(), anyLong())
        command = ExportBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Export with range and block file if cid provided`() {
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
        verify(postchainService).exportBlockchain(chainId, ByteArray(0), Path.of(configurationsFile), Path.of(blocksFile), false, fromHeight, upToHeight)
        testConsole.assertContains("Export of 10 blocks 10..20 to configurationsFile and blocksFile completed")
    }

    @Test
    fun `Export with range and block file if brid provided`() {
        // execute
        command.parse(
                arrayOf(
                        "-brid", brid,
                        "--configurations-file", configurationsFile,
                        "--blocks-file", blocksFile,
                        "--from-height", fromHeight.toString(),
                        "--up-to-height", upToHeight.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).exportBlockchain(0, BlockchainRid.buildFromHex(brid).data, Path.of(configurationsFile), Path.of(blocksFile), false, fromHeight, upToHeight)
        testConsole.assertContains("Export of 10 blocks 10..20 to configurationsFile and blocksFile completed")
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
        verify(postchainService).exportBlockchain(chainId, ByteArray(0), Path.of(configurationsFile), null, false, fromHeight, upToHeight)
        testConsole.assertContains("Export of configurations to configurationsFile completed")
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
        verify(postchainService).exportBlockchain(chainId, ByteArray(0), Path.of(configurationsFile), null, false, 0L, Long.MAX_VALUE)
        testConsole.assertContains("Export of configurations to configurationsFile completed")
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
        verify(postchainService).exportBlockchain(chainId, ByteArray(0), Path.of(configurationsFile), Path.of(blocksFile), false, 0L, Long.MAX_VALUE)
        testConsole.assertContains("Export of 10 blocks 10..20 to configurationsFile and blocksFile completed")
    }
}