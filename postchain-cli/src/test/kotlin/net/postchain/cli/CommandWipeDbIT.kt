package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import org.junit.jupiter.api.Test

class CommandWipeDbIT : CommandITBase() {

    @Test
    fun `Remove configuration`() {
        // setup
        val command = CommandWipeDb()
        command.context { terminal = testTerminal.terminal }
        addBlockchain()
        assertThat(CliExecution.findBlockchainRid(appConfig, chainId)).isNotNull()
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath
                )
        )
        // verify
        assertThat(CliExecution.findBlockchainRid(appConfig, chainId)).isNull()
        testTerminal.assertContains("Database has been wiped successfully\n")
    }
}