package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.admin.cli.util.TlsConfig

class PostchainAdminClientCommand : CliktCommand(
        name = "postchain-admin-client",
        help = "Client for communicating with postchain running in server mode.",
) {
    private val tls by option("--tls", envvar = "POSTCHAIN_TLS").flag()

    @Deprecated("Use --tls instead")
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
        currentContext.findOrSetObject {
            @Suppress("DEPRECATION")
            TlsConfig(tls or ssl, certificateFile)
        }
    }
}

fun main(args: Array<String>) = PostchainAdminClientCommand()
        .subcommands(
                StartBlockchainCommand(),
                StopBlockchainCommand(),
                AddConfigurationCommand(),
                InitializeBlockchainCommand(),
                AddBlockchainReplicaCommand(),
                RemoveBlockchainReplicaCommand(),

                AddPeerCommand(),
                RemovePeerCommand(),
                ListPeersCommand(),

                DebugCommand(),

                HealthCommand(),
        )
        .main(args)
