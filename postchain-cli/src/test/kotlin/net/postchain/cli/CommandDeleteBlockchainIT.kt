package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandDeleteBlockchainIT : CommandITBase() {

    private lateinit var command: CommandDeleteBlockchain

    @BeforeEach
    fun setup() {
        command = CommandDeleteBlockchain()
        command.context { terminal = testTerminal.terminal }
    }

    @Test
    fun `Delete blockchain`() {
        // setup
        addBlockchain()
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString()
                )
        )
        // verify
        assertThat(CliExecution.findBlockchainRid(appConfig, chainId)).isNull()
        testTerminal.assertContains("OK: Blockchain was deleted\n")
    }

    @Test
    fun `Delete missing blockchain`() {
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    listOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-cid", chainId.toString()
                    )
            )
        }
        // verify
        assertThat(exception.message).isEqualTo("Blockchain RID not found")
    }
}