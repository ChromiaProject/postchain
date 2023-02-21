package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.StopBlockchainRequest

class StopBlockchainCommand: CliktCommand(help = "Stop blockchain with id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val request = StopBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.stopBlockchain(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
