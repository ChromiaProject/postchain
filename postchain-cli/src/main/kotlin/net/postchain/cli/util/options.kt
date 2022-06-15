package net.postchain.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray


fun CliktCommand.blockchainConfigOption() = option(
    "-bc",
    "--blockchain-config",
    help = "Configuration file of blockchain (GtvML (*.xml) or Gtv (*.gtv))"
).required()

fun CliktCommand.blockchainRidOption() =
    option("-brid", "--blockchain-rid", help = "Blockchain RID").convert { BlockchainRid.buildFromHex(it) }.required()

fun CliktCommand.chainIdOption() = option("-cid", "--chain-id", help = "Local number id of blockchain").long()

fun CliktCommand.debugOption() = option("--debug", "Enables debug functionalities").flag()

fun CliktCommand.forceOption() = option("-f", "--force").flag()

fun CliktCommand.heightOption() = option("-h", "--height").long()

fun CliktCommand.hostOption() = option("-h", "--host", help = "Host")

fun CliktCommand.infrastructureOption() =
    option("-i", "--infrastructure", help = "The type of blockchain infrastructure.").default("base/ebft")

fun CliktCommand.nodeConfigOption() =
    option("-nc", "--node-config", help = "Configuration file of node (.properties file)").required()

fun CliktCommand.portOption() = option("-p", "--port", help = "Port").int()

fun CliktCommand.pubkeyOption() = option("-pk", "--pubkey", help = "Public key").validate { validatePubkey(it) }

fun CliktCommand.requiredPubkeyOption() =
    option("-pk", "--pubkey", help = "Public key").required().validate { validatePubkey(it) }

private fun validatePubkey(it: String) {
    require(it.length == 66) { "Public key must have length 66" }
    try {
        it.hexStringToByteArray()
    } catch (e: Exception) {
        require(false) { e.message!! + "Allowed characters in public keys are 0-9, a-f, A-F" }
    }
}
