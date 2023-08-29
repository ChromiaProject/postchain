package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.ajalt.clikt.core.context
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandRemoveConfigurationIT : CommandITBase() {

    private lateinit var command: CommandRemoveConfiguration

    @BeforeEach
    fun setup() {
        command = CommandRemoveConfiguration()
        command.context { console = testConsole }
        addBlockchain(multiSignersBlockchainConfig)
    }

    @Test
    fun `Remove configuration`() {
        // setup
        addConfiguration()
        assertThat(CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigFile), chainId, heightSecondConfig)).isNotNull()
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString()
                )
        )
        // verify
        assertThat(CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigFile), chainId, heightSecondConfig)).isNull()
        testConsole.assertContains("Removed configuration at height $heightSecondConfig\n")
    }

    @Test
    fun `Remove missing configuration`() {
        // setup
        assertThat(CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigFile), chainId, heightSecondConfig)).isNull()
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString()
                )
        )
        // verify
        testConsole.assertContains("Can't find configuration at height: $heightSecondConfig\n")
    }

    private fun addConfiguration() {
        CommandAddConfiguration().parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-bc", updatedBlockChainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString()
                )
        )
    }
}