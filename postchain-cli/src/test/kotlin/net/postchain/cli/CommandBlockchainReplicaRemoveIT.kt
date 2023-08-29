package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.ajalt.clikt.core.context
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.base.withReadConnection
import net.postchain.common.hexStringToByteArray
import net.postchain.core.AppContext
import net.postchain.crypto.PubKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandBlockchainReplicaRemoveIT : CommandITBase() {

    private lateinit var command: CommandBlockchainReplicaRemove

    @BeforeEach
    fun setup() {
        command = CommandBlockchainReplicaRemove()
        command.context { console = testConsole }
        addBlockchain(multiSignersBlockchainConfig)
        addSignersAsPeers()
    }

    @Test
    fun `Remove blockchain replica`() {
        // setup
        addReplica()
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer3PubKey,
                        "-brid", brid
                )
        )
        // verify
        withReadConnection(storage, chainId) {
            assertThat(DatabaseAccess.of(it).existsBlockchainReplica(it, blockchainRID, PubKey(signer3PubKey.hexStringToByteArray()))).isFalse()
        }
        testConsole.assertContains("Replica $signer3PubKey removed from brid (1):\n$brid\n")
    }

    @Test
    fun `Remove blockchain replica with no replica`() {
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer3PubKey,
                        "-brid", brid
                )
        )
        // verify
        testConsole.assertContains("No replica has been removed\n")
    }

    private fun addReplica() {
        runStorageCommand(appConfig) { ctx: AppContext ->
            BlockchainApi.addBlockchainReplica(ctx, blockchainRID, PubKey(signer3PubKey.hexStringToByteArray()))
            assertThat(DatabaseAccess.of(ctx).existsBlockchainReplica(ctx, blockchainRID, PubKey(signer3PubKey.hexStringToByteArray()))).isTrue()
        }
    }
}