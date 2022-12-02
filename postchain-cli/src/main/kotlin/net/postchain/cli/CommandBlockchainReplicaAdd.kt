// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption

class CommandBlockchainReplicaAdd : CliktCommand(name = "blockchain-replica-add", help = "Add info to system about blockchain replicas. To be used to sync this node.") {

    private val nodeConfigFile by nodeConfigOption()

    private val pubKey by requiredPubkeyOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        val added = runStorageCommand(nodeConfigFile) { ctx ->
            BlockchainApi.addBlockchainReplica(ctx, blockchainRID, pubKey)
        }

        return when {
            added -> println("$commandName finished successfully")
            else -> println("Blockchain replica already exists")
        }
    }
}