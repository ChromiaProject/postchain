package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.base.importexport.ImportResult
import net.postchain.common.BlockchainRid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ImportBlockchainCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val configurationsFile = "configurationsFile"
    private val blocksFile = "blocksFile"
    private val importResult = ImportResult(1, 10, 3, 1, 8, BlockchainRid.buildFromHex(brid))

    private lateinit var command: ImportBlockchainCommand

    @BeforeEach
    fun beforeEach() {
        doReturn(importResult).whenever(postchainService).importBlockchain(anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyBoolean())
        command = ImportBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Import with brid`() {
        // execute
        command.parse(
                arrayOf(
                        "-brid", brid,
                        "--configurations-file", configurationsFile,
                        "--blocks-file", blocksFile,
                        "--incremental",
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).importBlockchain(0, BlockchainRid.buildFromHex(brid).data, Path.of(configurationsFile), Path.of(blocksFile), true)
        testConsole.assertContains("Import of 8 blocks 1..10 to chain $brid completed")
    }

    @Test
    fun `Import with chain id`() {
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile,
                        "--blocks-file", blocksFile,
                        "--incremental",
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).importBlockchain(chainId, ByteArray(0), Path.of(configurationsFile), Path.of(blocksFile), true)
        testConsole.assertContains("Import of 8 blocks 1..10 to chain $brid completed")
    }
}