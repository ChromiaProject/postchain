package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.common.BlockchainRid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FindBlockchainCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val height = 10L

    private lateinit var command: FindBlockchainCommand

    @BeforeEach
    fun beforeEach() {
        command = FindBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Find blockchain`() {
        // setup
        doReturn(Triple(BlockchainRid.buildFromHex(brid), true, height)).whenever(postchainService).findBlockchain(anyLong())
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).findBlockchain(chainId)
        testConsole.assertContains("$brid\n")
    }

    @Test
    fun `Find blockchain with missing blockchain`() {
        // setup
        doReturn(Triple(null, true, 0)).whenever(postchainService).findBlockchain(anyLong())
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).findBlockchain(chainId)
        testConsole.assertContains("\n")
    }
}