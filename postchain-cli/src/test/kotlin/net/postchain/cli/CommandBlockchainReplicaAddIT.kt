package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isTrue
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.hexStringToByteArray
import net.postchain.core.AppContext
import net.postchain.crypto.PubKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandBlockchainReplicaAddIT : CommandITBase() {

    private lateinit var command: CommandBlockchainReplicaAdd

    @BeforeEach
    fun setup() {
        command = CommandBlockchainReplicaAdd()
        command.context { terminal = testTerminal.terminal }
        addBlockchain(multiSignersBlockchainConfig)
        addSignersAsPeers()
    }

    @Test
    fun `Add blockchain replica`() {
        // execute
        addReplica()
        // verify
        runStorageCommand(appConfig) { ctx: AppContext ->
            assertThat(DatabaseAccess.of(ctx).existsBlockchainReplica(ctx, blockchainRID, PubKey(signer3PubKey.hexStringToByteArray()))).isTrue()
        }
        testTerminal.assertContains("Blockchain replica added successfully\n")
    }

    @Test
    fun `Add blockchain replica twice should fail`() {
        // setup
        addReplica()
        // execute
        addReplica()
        // verify
        testTerminal.assertContains(
                listOf(
                        "Blockchain replica added successfully",
                        "Blockchain replica already exists"
                )
        )
    }

    private fun addReplica() {
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-pk", signer3PubKey,
                        "-brid", brid
                )
        )
    }
}