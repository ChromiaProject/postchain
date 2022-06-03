package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.subcommands


fun main(args: Array<String>) = PostchainRpcClientCommand()
        .subcommands(
                AddConfigurationCommand(),
                AddPeerCommand(),
                InitializeBlockchainCommand(),
                StartBlockchainCommand(),
                StopBlockchainCommand(),
        )
        .main(args)
