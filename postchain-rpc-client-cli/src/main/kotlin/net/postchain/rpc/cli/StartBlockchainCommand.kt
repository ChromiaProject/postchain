package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import io.grpc.StatusRuntimeException
import net.postchain.rpc.cli.util.clientArgument

class StartBlockchainCommand: CliktCommand(help = "Start blockchain with id") {

    private val client by clientArgument()

    private val chainId by argument(help = "Chain ID to start").long()

    override fun run() {
        try {
            client.startBlockchain(chainId)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}