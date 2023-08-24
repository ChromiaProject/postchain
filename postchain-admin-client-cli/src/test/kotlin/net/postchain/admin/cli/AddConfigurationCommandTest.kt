package net.postchain.admin.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddConfigurationCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val height = 10L
    private val blockChainConfig = getResouceFile("blockchain_config.xml")
    private val gtv = GtvMLParser.parseGtvML(blockChainConfig.readText())

    private lateinit var command: AddConfigurationCommand

    @BeforeEach
    fun beforeEach() {
        command = AddConfigurationCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Add configuration`() {
        // setup
        doReturn(true).whenever(postchainService).addConfiguration(anyLong(), anyLong(), anyBoolean(), isA(), anyBoolean())
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "-bc", blockChainConfig.absolutePath,
                        "--height", height.toString(),
                        "-f",
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).addConfiguration(chainId, height, true, gtv, false)
        testConsole.assertContains("Configuration height $height on chain $chainId has been added\n")
    }

    @Test
    fun `Add configuration to already existing configuration height should not add configuration`() {
        // setup
        doReturn(false).whenever(postchainService).addConfiguration(anyLong(), anyLong(), anyBoolean(), isA(), anyBoolean())
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "-cid", chainId.toString(),
                            "-bc", blockChainConfig.absolutePath,
                            "--height", height.toString(),
                            "--target", "localhost:1234"
                    )
            )
        }
        // verify
        verify(postchainService).addConfiguration(chainId, height, false, gtv, false)
        assertThat(exception.message).isEqualTo("Failed with: ALREADY_EXISTS: Configuration already exists for height $height on chain $chainId")
    }
}