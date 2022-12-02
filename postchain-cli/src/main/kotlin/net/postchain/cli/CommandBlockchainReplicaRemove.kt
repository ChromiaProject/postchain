package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption

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
        val removed = runStorageCommand(nodeConfigFile) { ctx ->
            BlockchainApi.removeBlockchainReplica(ctx, blockchainRID.toHex(), pubKey.hex())
        }

        if (removed.isEmpty()) {
            println("No replica has been removed")
        } else {
            removed.forEach {
                println("Replica $pubKey removed from brid (${removed.size}):\n$it")
            }
        }
    }
}
