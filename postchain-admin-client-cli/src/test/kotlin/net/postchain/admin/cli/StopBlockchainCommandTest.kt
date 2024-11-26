package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StopBlockchainCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L

    @Test
    fun `Stop blockchain`() {
        // setup
        doNothing().whenever(postchainService).stopBlockchain(anyLong())
        val command = StopBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { terminal = testTerminal.terminal }
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).stopBlockchain(chainId)
        testTerminal.assertContains("Blockchain has been stopped")
    }
}