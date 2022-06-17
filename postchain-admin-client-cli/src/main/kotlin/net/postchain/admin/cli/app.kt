package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.admin.cli.util.SslConfig

class PostchainAdminClientCommand : CliktCommand(
    name = "postchain-admin-client",
    help = "Client for communicating with postchain running in server mode.",
) {
    private val ssl by option("--ssl", envvar = "POSTCHAIN_SSL").flag()

    private val certificateFile by option(
        "-cf",
        "--certificate-file",
        help = "Custom certificate file",
        envvar = "POSTCHAIN_CERTIFICATE"
    )
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    override fun run() {
        // Store in context so that subcommands can use it
        currentContext.findOrSetObject { SslConfig(ssl, certificateFile) }
    }
}

fun main(args: Array<String>) = PostchainAdminClientCommand()
    .subcommands(
        StartBlockchainCommand(),
        StopBlockchainCommand(),
        AddConfigurationCommand(),
        InitializeBlockchainCommand(),

        AddPeerCommand(),
        RemovePeerCommand(),
        ListPeersCommand(),

        DebugCommand(),
    )
    .main(args)
