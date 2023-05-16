package net.postchain.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.cli.AlreadyExistMode
import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey

fun CliktCommand.nodeConfigOption() =
        option("-nc", "--node-config", help = "Configuration file of node (.properties file)", envvar = "POSTCHAIN_CONFIG")
                .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

fun CliktCommand.blockchainConfigOption() = option(
        "-bc",
        "--blockchain-config",
        help = "Configuration file of blockchain (GtvML (*.xml) or Gtv (*.gtv))",
        envvar = "POSTCHAIN_BLOCKCHAIN_CONFIG"
).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

fun CliktCommand.requiredPubkeyOption() = option("-pk", "--pubkey", help = "Public key")
        .convert { PubKey(it) }.required()

fun CliktCommand.blockchainRidOption() =
        option("-brid", "--blockchain-rid", help = "Blockchain RID", envvar = "POSTCHAIN_BRID")
                .convert { BlockchainRid.buildFromHex(it) }.required()

fun CliktCommand.chainIdOption() = option("-cid", "--chain-id", help = "Local number id of blockchain", envvar = "POSTCHAIN_CHAIN_ID").long()

fun CliktCommand.forceOption() = option("-f", "--force").flag()
        .convert { if (it) AlreadyExistMode.FORCE else AlreadyExistMode.ERROR }

fun CliktCommand.heightOption() = option("-h", "--height", envvar = "POSTCHAIN_HEIGHT").long()

fun CliktCommand.hostOption() = option("-h", "--host", help = "Host", envvar = "POSTCHAIN_HOST")

fun CliktCommand.portOption() = option("-p", "--port", help = "Port").int()
