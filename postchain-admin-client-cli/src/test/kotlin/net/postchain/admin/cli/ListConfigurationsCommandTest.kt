package net.postchain.admin.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.common.BlockchainRid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ListConfigurationsCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val height = 10L

    private lateinit var command: ListConfigurationsCommand

    @BeforeEach
    fun beforeEach() {
        command = ListConfigurationsCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `List configurations should return height for all configurations`() {
        // setup
        val configHeights = listOf(1L, 3L, 5L, 7L)
        doReturn(Triple(BlockchainRid.buildFromHex(brid), true, height)).whenever(postchainService).findBlockchain(anyLong())
        doReturn(configHeights).whenever(postchainService).listConfigurations(anyLong())
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).findBlockchain(chainId)
        verify(postchainService).listConfigurations(chainId)
        testConsole.assertContains(listOf("Height", "------", "1", "3", "5", "7"))
    }

    @Test
    fun `List configuration for missing blockchain`() {
        // setup
        doReturn(Triple(null, true, 0)).whenever(postchainService).findBlockchain(anyLong())
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "-cid", chainId.toString(),
                            "--target", "localhost:1234"
                    )
            )
        }
        // verify
        verify(postchainService).findBlockchain(chainId)
        assertThat(exception.message).isEqualTo("Failed with: NOT_FOUND: Blockchain not found: $chainId")
    }
}