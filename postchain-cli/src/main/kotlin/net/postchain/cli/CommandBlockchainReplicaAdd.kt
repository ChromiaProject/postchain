// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo
import net.postchain.cli.util.requiredPubkeyOption

class CommandBlockchainReplicaAdd : CliktCommand(name = "blockchain-replica-add", help = "Add info to system about blockchain replicas. To be used to sync this node.") {

    private val nodeConfigFile by nodeConfigOption()

    private val pubKey by requiredPubkeyOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        printCommandInfo()

        val added = addReplica(blockchainRID.toHex(), pubKey)
        return when {
            added -> println("$commandName finished successfully")
            else -> println("Blockchain replica already exists")
        }
    }

    private fun addReplica(brid: String, pubKey: String): Boolean {
        return runStorageCommand(nodeConfigFile) { ctx ->
            val db = DatabaseAccess.of(ctx)

            // Node must be in PeerInfo, or else it is not allowed as blockchain replica.
            val foundInPeerInfo = db.findPeerInfo(ctx, null, null, pubKey)
            if (foundInPeerInfo.isEmpty()) {
                throw CliException("Given pubkey is not a peer. First add it as a peer.")
            }

            db.addBlockchainReplica(ctx, brid, pubKey)
        }
    }
}