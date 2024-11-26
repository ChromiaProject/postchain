package net.postchain.cli

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import org.junit.jupiter.api.Test

class CommandListConfigurationsIT : CommandITBase() {

    @Test
    fun `List configurations`() {
        // setup
        val command = CommandListConfigurations()
        command.context { terminal = testTerminal.terminal }
        addBlockchain()
        addConfiguration()
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString()
                )
        )
        // verify
        testTerminal.assertContains(
                listOf(
                        "Height",
                        "------",
                        "0",
                        "10"
                )
        )
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