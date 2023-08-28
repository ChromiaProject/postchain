package net.postchain.cli

import com.github.ajalt.clikt.core.context
import org.junit.jupiter.api.Test

class CommandListConfigurationsIT : CommandITBase() {

    @Test
    fun `List configurations`() {
        // setup
        val command = CommandListConfigurations()
        command.context { console = testConsole }
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
        testConsole.assertContains(
                listOf(
                        "Height\n",
                        "------\n",
                        "0\n",
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