package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.StartBlockchainRequest

class StartBlockchainCommand: CliktCommand(help = "Start blockchain with id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val request = StartBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.startBlockchain(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
