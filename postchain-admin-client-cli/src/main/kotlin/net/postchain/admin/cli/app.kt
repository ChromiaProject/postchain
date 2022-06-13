package net.postchain.admin.cli

import com.github.ajalt.clikt.core.subcommands


fun main(args: Array<String>) = PostchainRpcClientCommand()
        .subcommands(
                AddConfigurationCommand(),
                AddPeerCommand(),
                DebugCommand(),
                InitializeBlockchainCommand(),
                ListPeersCommand(),
                RemovePeerCommand(),
                StartBlockchainCommand(),
                StopBlockchainCommand(),
        )
        .main(args)
