package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.context
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandMustSyncUntilIT : CommandITBase() {

    private lateinit var command: CommandMustSyncUntil

    @BeforeEach
    fun setup() {
        command = CommandMustSyncUntil()
        command.context { console = testConsole }
        addBlockchain()
    }

    @Test
    fun `Set must sync until`() {
        // execute & verify
        mustSyncUntilIsUpdated(20L)
        // verify
        testConsole.assertContains("Successfully set must sync until 20\n")
    }

    @Test
    fun `Set must sync until should overwrite height`() {
        // setup
        mustSyncUntilIsUpdated(20L)
        // execute
        mustSyncUntilIsUpdated(30L)
        // verify
        testConsole.assertContains(
                listOf(
                        "Successfully set must sync until 20\n",
                        "Successfully set must sync until 30\n"
                )
        )
    }

    private fun mustSyncUntilIsUpdated(height2: Long) {
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-brid", brid,
                        "--height", height2.toString()
                )
        )
        withReadConnection(storage, chainId) {
            assertThat(DatabaseAccess.of(it).getMustSyncUntil(it)[chainId]).isEqualTo(height2)
        }
    }
}