package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.core.AppContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandPeerInfoRemoveIT : CommandITBase() {

    private lateinit var command: CommandPeerInfoRemove

    @BeforeEach
    fun setup() {
        command = CommandPeerInfoRemove()
        command.context { terminal = testTerminal.terminal }
        addBlockchain(multiSignersBlockchainConfig)
    }

    @Test
    fun `Remove peer info with missing peer`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer1PubKey
                )
        )
        // verify
        testTerminal.assertContains("No peer info has been removed\n")
    }

    @Test
    fun `Remove peer info`() {
        // setup
        addSignersAsPeers()
        runStorageCommand(appConfig) { ctx: AppContext ->
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer1PubKey)).isNotEmpty()
        }
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer1PubKey
                )
        )
        // verify
        runStorageCommand(appConfig) { ctx: AppContext ->
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer1PubKey)).isEmpty()
        }
        testTerminal.assertContains(
                listOf(
                        "Peer info removed (1):\n  1:\t$host:$port1\t$signer1PubKey"
                )
        )
    }
}