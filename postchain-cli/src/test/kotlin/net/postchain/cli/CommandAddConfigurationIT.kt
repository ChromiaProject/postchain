package net.postchain.cli

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandAddConfigurationIT : CommandITBase() {

    private lateinit var command: CommandAddConfiguration

    @BeforeEach
    fun setup() {
        command = CommandAddConfiguration()
        command.context { console = testConsole }
        addBlockchain()
    }

    @Test
    fun `Add Configuration`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-bc", updatedBlockChainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString()
                )
        )
        // verify
        assertThat(CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigFile), chainId, heightSecondConfig)).isNotNull()
        testConsole.assertContains("Configuration has been added successfully\n")
    }

    @Test
    fun `Add invalid configuration`() {
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-bc", invalidBlockChainConfig.absolutePath,
                            "-cid", chainId.toString(),
                            "--height", heightSecondConfig.toString(),
                            "--allow-unknown-signers",
                            "--force"
                    )
            )
        }
        // verify
        assertThat(CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigFile), chainId, heightSecondConfig)).isNull()
        assertThat(exception.message).isEqualTo("Can't add configuration: Failed to decode field txqueuecapacity: Type error: integer expected, found STRING with value \"bogus\"")
    }

    @Test
    fun `Add configuration with missing peer info`() {
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-bc", multiSignersBlockchainConfig.absolutePath,
                            "-cid", chainId.toString(),
                            "--height", heightSecondConfig.toString(),
                            "--force"
                    )
            )
        }
        // verify
        assertThat(CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigFile), chainId, heightSecondConfig)).isNull()
        assertThat(exception.message).isEqualTo("Can't add configuration: Signer $signer1PubKey does not exist in peerinfos. Please add node with command peerinfo-add or set flag --allow-unknown-signers.")
    }

    @Test
    fun `Add configuration and allow unknown signers`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-bc", multiSignersBlockchainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString(),
                        "--allow-unknown-signers",
                        "--force"
                )
        )
        // verify
        assertThat(CliExecution.getConfiguration(appConfig, chainId, heightSecondConfig)).isNotNull()
        assertThat(CliExecution.listConfigurations(appConfig, chainId)).contains(heightSecondConfig)
    }

    @Test
    fun `Add configuration with peers added`() {
        // setup
        addSignersAsPeers()
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-bc", multiSignersBlockchainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString(),
                        "--force"
                )
        )
        // verify
        assertThat(CliExecution.getConfiguration(appConfig, chainId, heightSecondConfig)).isNotNull()
        assertThat(CliExecution.listConfigurations(appConfig, chainId)).contains(heightSecondConfig)
    }
}