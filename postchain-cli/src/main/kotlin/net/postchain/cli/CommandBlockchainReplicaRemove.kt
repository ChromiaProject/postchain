package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext

class CommandBlockchainReplicaRemove : CliktCommand(
        name = "blockchain-replica-remove",
        help = "Remove node as replica for given blockchain rid. If brid not given command will be " +
                "applied on all blockchains."
) {
    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption().required()

    private val blockchainRID by blockchainRidOption()

    private val pubKey by requiredPubkeyOption()


    override fun run() {
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        val removed = runStorageCommand(appConfig) { ctx: AppContext ->
            BlockchainApi.removeBlockchainReplica(ctx, blockchainRID, pubKey)
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
