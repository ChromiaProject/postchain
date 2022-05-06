package net.postchain.client

import com.github.ajalt.clikt.core.subcommands
import net.postchain.client.cli.PostTxCommand
import net.postchain.client.cli.PostchainClient
import net.postchain.client.cli.QueryCommand
import net.postchain.client.core.ConcretePostchainClientProvider

val clientProvider = ConcretePostchainClientProvider()

fun main(args: Array<String>) = PostchainClient()
        .subcommands(
                PostTxCommand(clientProvider),
                QueryCommand(clientProvider)
        )
        .main(args)
