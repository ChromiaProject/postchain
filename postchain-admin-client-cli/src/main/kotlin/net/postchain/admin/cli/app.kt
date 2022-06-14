package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands


class PostchainAdminClientCommand : CliktCommand(
    name = "postchain-admin-client",
    help = "Client for communicating with postchain running in server mode.",
) {
    override fun run() = Unit
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
