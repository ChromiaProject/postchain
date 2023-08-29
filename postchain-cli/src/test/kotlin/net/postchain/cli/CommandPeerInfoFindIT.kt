package net.postchain.cli

import com.github.ajalt.clikt.core.context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandPeerInfoFindIT : CommandITBase() {

    private lateinit var command: CommandPeerInfoFind

    @BeforeEach
    fun setup() {
        command = CommandPeerInfoFind()
        command.context { console = testConsole }
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
        testConsole.assertContains(
                listOf(
                        "Peer infos (1):\n  1:\t$host:$port3\t$signer3PubKey\n"
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
        testConsole.assertContains(
                listOf(
                        "Peer infos (4):\n  1:\t$host:$port1\t$signer1PubKey\n",
                        "Peer infos (4):\n  2:\t$host:$port2\t$signer2PubKey\n",
                        "Peer infos (4):\n  3:\t$host:$port3\t$signer3PubKey\n",
                        "Peer infos (4):\n  4:\t$host:$port4\t$signer4PubKey\n"
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
        testConsole.assertContains(
                listOf(
                        "Peer infos (1):\n  1:\t$host:$port2\t$signer2PubKey\n"
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
        testConsole.assertContains("No peer info found\n")
    }
}