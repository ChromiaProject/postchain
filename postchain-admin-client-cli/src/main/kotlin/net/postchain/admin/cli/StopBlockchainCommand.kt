package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.service.StopBlockchainRequest

class StopBlockchainCommand: CliktCommand(help = "Stop blockchain with id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

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
