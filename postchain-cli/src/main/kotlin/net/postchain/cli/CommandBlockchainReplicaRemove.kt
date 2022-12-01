package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.BlockchainRid

class CommandBlockchainReplicaRemove : CliktCommand(
        name = "blockchain-replica-remove",
        help = "Remove node as replica for given blockchain rid. If brid not given command will be " +
                "applied on all blockchains."
) {
    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val pubKey by requiredPubkeyOption()


    override fun run() {
        val removed = blockchainReplicaRemove(blockchainRID.toHex(), pubKey)

        if (removed.isEmpty()) {
            println("No replica has been removed")
        } else {
            removed.forEach {
                println("Replica $pubKey removed from brid (${removed.size}):\n$it")
            }
        }


    }

    private fun blockchainReplicaRemove(brid: String?, pubKey: String): Set<BlockchainRid> {
        return runStorageCommand(nodeConfigFile) { ctx ->
            BlockchainApi.removeBlockchainReplica(ctx, brid, pubKey)
        }
    }
}
