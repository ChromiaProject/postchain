package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandAddBlockchainIT : CommandITBase() {

    private lateinit var command: CommandAddBlockchain

    @BeforeEach
    fun setup() {
        command = CommandAddBlockchain()
        command.context { console = testConsole }
    }

    @Test
    fun `Add blockchain`() {
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-bc", blockchainConfig.absolutePath,
                        "-cid", chainId.toString()
                )
        )
        // verify
        assertThat(CliExecution.findBlockchainRid(AppConfig.fromPropertiesFile(nodeConfigFile), chainId)).isNotNull()
        testConsole.assertContains("Blockchain has been added successfully\n")
    }

    @Test
    fun `Add blockchain twice should fail`() {
        // setup
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-bc", blockchainConfig.absolutePath,
                        "-cid", chainId.toString()
                )
        )
        // execute & verify
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    listOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-bc", blockchainConfig.absolutePath,
                            "-cid", chainId.toString()
                    )
            )
        }
        assertThat(CliExecution.findBlockchainRid(AppConfig.fromPropertiesFile(nodeConfigFile), chainId)).isNotNull()
        assertThat(exception.message).isEqualTo("Can't add blockchain: Blockchain with chainId $chainId already exists. Use -f flag to force addition.")
    }

    @Test
    fun `Add blockchain with bad configuration should fail`() {
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    listOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-bc", invalidBlockChainConfig.absolutePath,
                            "-cid", chainId.toString()
                    )
            )
        }
        assertThat(CliExecution.findBlockchainRid(AppConfig.fromPropertiesFile(nodeConfigFile), chainId)).isNull()
        assertThat(exception.message).isEqualTo("Can't add blockchain: Failed to decode field txqueuecapacity: Type error: integer expected, found STRING with value \"bogus\"")
    }
}