package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import io.grpc.StatusRuntimeException
import net.postchain.rpc.cli.util.blockingChannelOption
import net.postchain.server.rpc.StopBlockchainRequest

class StopBlockchainCommand: CliktCommand(help = "Stop blockchain with id") {

    private val channel by blockingChannelOption()

    private val chainId by argument(help = "Chain ID to stop").long()

    override fun run() {
        try {
            val request = StopBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.stopBlockchain(request)
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}