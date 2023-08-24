package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.common.BlockchainRid
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StartBlockchainCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockchainRid = BlockchainRid.buildFromHex(brid)

    @Test
    fun `Start blockchain`() {
        // setup
        doReturn(blockchainRid).whenever(postchainService).startBlockchain(anyLong())
        val command = StartBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).startBlockchain(chainId)
        testConsole.assertContains("Blockchain with id ${chainId} started with brid $brid\n")
    }
}