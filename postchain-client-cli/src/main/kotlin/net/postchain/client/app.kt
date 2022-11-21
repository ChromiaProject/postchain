package net.postchain.client

import com.github.ajalt.clikt.core.subcommands
import net.postchain.client.base.ConcretePostchainClientProvider
import net.postchain.client.cli.CurrentBlockHeightCommand
import net.postchain.client.cli.PostTxCommand
import net.postchain.client.cli.PostchainClient
import net.postchain.client.cli.QueryCommand

val clientProvider = ConcretePostchainClientProvider()

fun main(args: Array<String>) = PostchainClient()
        .subcommands(
                PostTxCommand(clientProvider),
                QueryCommand(clientProvider),
                CurrentBlockHeightCommand(clientProvider)
        )
        .main(args)
