package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import com.github.ajalt.clikt.core.context
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.core.AppContext
import org.junit.jupiter.api.Test

class CommandPeerInfoImportIT : CommandITBase() {

    @Test
    fun `Import peer info from node config`() {
        // setup
        val command = CommandPeerInfoImport()
        command.context { console = testConsole }
        addBlockchain(multiSignersBlockchainConfig)
        runStorageCommand(appConfig) { ctx: AppContext ->
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer1PubKey)).isEmpty()
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer2PubKey)).isEmpty()
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer3PubKey)).isEmpty()
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer4PubKey)).isEmpty()
        }
        // execute
        command.parse(
                arrayOf(
                        "-nc", nodeConfigFile.absolutePath
                )
        )
        // verify
        runStorageCommand(appConfig) { ctx: AppContext ->
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer1PubKey)).isNotEmpty()
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer2PubKey)).isNotEmpty()
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer3PubKey)).isNotEmpty()
            assertThat(PeerApi.findPeerInfo(ctx, null, null, signer4PubKey)).isNotEmpty()
        }
        testConsole.assertContains(
                listOf(
                        "Peer info added (4):\n  1:\t$host:$port1\t$signer1PubKey\n",
                        "Peer info added (4):\n  2:\t$host:$port2\t$signer2PubKey\n",
                        "Peer info added (4):\n  3:\t$host:$port3\t$signer3PubKey\n",
                        "Peer info added (4):\n  4:\t$host:$port4\t$signer4PubKey\n"
                )
        )
    }
}