package net.postchain.cli

import com.github.ajalt.clikt.core.context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandPeerInfoListIT : CommandITBase() {

    private lateinit var command: CommandPeerInfoList

    @BeforeEach
    fun setup() {
        command = CommandPeerInfoList()
        command.context { console = testConsole }
        addBlockchain(multiSignersBlockchainConfig)
    }

    @Test
    fun `List peer infos`() {
        // setup
        addSignersAsPeers()
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath
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
    fun `List peer infos with no peers`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath
                )
        )
        // verify
        testConsole.assertContains("No peer info found\n")
    }
}