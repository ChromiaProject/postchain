package net.postchain.client

import com.github.ajalt.clikt.core.subcommands
import net.postchain.client.cli.PostTxCommand
import net.postchain.client.cli.PostchainClient
import net.postchain.client.cli.QueryCommand

fun main(args: Array<String>) = PostchainClient()
        .subcommands(
                PostTxCommand(),
                QueryCommand()
        )
        .main(args)
