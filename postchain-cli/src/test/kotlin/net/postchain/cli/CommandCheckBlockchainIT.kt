package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandCheckBlockchainIT : CommandITBase() {

    private lateinit var command: CommandCheckBlockchain

    @BeforeEach
    fun setup() {
        command = CommandCheckBlockchain()
        command.context { console = testConsole }
        addBlockchain()
    }

    @Test
    fun `Check blockchain`() {
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-brid", brid,
                        "-cid", chainId.toString()
                )
        )
        // verify
        testConsole.assertContains("OK: blockchain with specified chainId and blockchainRid exists\n")
    }

    @Test
    fun `Check blockchain missing blockchain`() {
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    listOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-brid", "1".repeat(64),
                            "-cid", chainId.toString()
                    )
            )
        }
        // verify
        assertThat(exception.message).isEqualTo("BlockchainRids are not equal:\n" +
                "    expected: 1111111111111111111111111111111111111111111111111111111111111111\n" +
                "    actual: $brid")
    }
}