package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.common.hexStringToByteArray
import net.postchain.core.AppContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandPeerInfoAddIT : CommandITBase() {

    private lateinit var command: CommandPeerInfoAdd

    @BeforeEach
    fun setup() {
        command = CommandPeerInfoAdd()
        command.context { console = testConsole }
        addBlockchain(multiSignersBlockchainConfig)
        addSignersAsPeers()
    }

    @Test
    fun `Add peer info`() {
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer3PubKey,
                        "-h", host,
                        "-p", "1234",
                        "-f"
                )
        )
        // verify
        val peerInfos = runStorageCommand(appConfig) { ctx: AppContext ->
            PeerApi.findPeerInfo(ctx, null, null, signer3PubKey)
        }
        assertThat(peerInfos.size).isEqualTo(1)
        assertThat(peerInfos[0].pubKey).isEqualTo(signer3PubKey.hexStringToByteArray())
        assertThat(peerInfos[0].host).isEqualTo(host)
        assertThat(peerInfos[0].port).isEqualTo(1234)
        testConsole.assertContains("Peer info has been added successfully\n")
    }

    @Test
    fun `Add peer info again without force`() {
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-pk", signer3PubKey,
                            "-h", host,
                            "-p", "1234"
                    )
            )
        }
        // verify
        assertThat(exception.message).isEqualTo("Can't add peer info: Peer info with pubkey $signer3PubKey already exists. Use -f to force update")
    }
}