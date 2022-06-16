package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.BlockchainRid
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle


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
        println(
            "blockchain-replica-remove will be executed with options: " +
                    ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
        )

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
        return runStorageCommand(nodeConfigFile) {
            DatabaseAccess.of(it).removeBlockchainReplica(it, brid, pubKey)
        }
    }
}
