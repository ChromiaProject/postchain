package net.postchain.cli

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandPeerInfoFindIT : CommandITBase() {

    private lateinit var command: CommandPeerInfoFind

    @BeforeEach
    fun setup() {
        command = CommandPeerInfoFind()
        command.context { terminal = testTerminal.terminal }
        addBlockchain(multiSignersBlockchainConfig)
        addSignersAsPeers()
    }

    @Test
    fun `Find peer info from pubkey`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer3PubKey
                )
        )
        // verify
        testTerminal.assertContains(
                listOf(
                        "Peer infos (1):\n  1:\t$host:$port3\t$signer3PubKey"
                )
        )
    }

    @Test
    fun `Find peer info from host`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-h", host
                )
        )
        // verify
        testTerminal.assertContains(
                listOf(
                        "Peer infos (4):\n  1:\t$host:$port1\t$signer1PubKey",
                        "Peer infos (4):\n  2:\t$host:$port2\t$signer2PubKey",
                        "Peer infos (4):\n  3:\t$host:$port3\t$signer3PubKey",
                        "Peer infos (4):\n  4:\t$host:$port4\t$signer4PubKey"
                )
        )
    }

    @Test
    fun `Find peer info from port`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-p", port2
                )
        )
        // verify
        testTerminal.assertContains(
                listOf(
                        "Peer infos (1):\n  1:\t$host:$port2\t$signer2PubKey"
                )
        )
    }

    @Test
    fun `Fail to find peer info from mismatched host and port`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-p", "1234",
                        "-h", host
                )
        )
        // verify
        testTerminal.assertContains("No peer info found\n")
    }
}