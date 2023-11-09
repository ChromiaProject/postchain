package net.postchain.server.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long

fun CliktCommand.nodeConfigOption() =
        option("-nc", "--node-config", help = "Configuration file of node (.properties file)", envvar = "POSTCHAIN_CONFIG")
                .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

fun CliktCommand.blockchainConfigOption() = option(
        "-bc",
        "--blockchain-config",
        help = "Configuration file of blockchain (GtvML (*.xml) or Gtv (*.gtv))",
        envvar = "POSTCHAIN_BLOCKCHAIN_CONFIG"
).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

fun CliktCommand.chainIdOption() = option("-cid", "--chain-id", help = "Local number id of blockchain", envvar = "POSTCHAIN_CHAIN_ID").long()

fun CliktCommand.deprecatedDebugOption() = option("--debug", help = "Enables debug functionalities", envvar = "POSTCHAIN_DEBUG").flag().deprecated("Debug is enabled by default")

fun CliktCommand.dumpPidOption() = option("--dump-pid", help = "Dump PID to file 'postchain.pid'").flag()
