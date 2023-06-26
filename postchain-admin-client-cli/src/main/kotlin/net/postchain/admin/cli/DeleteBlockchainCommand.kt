package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.RemoveBlockchainRequest

class DeleteBlockchainCommand : CliktCommand(name = "delete", help = "Delete a blockchain") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val requestBuilder = RemoveBlockchainRequest.newBuilder().setChainId(chainId)
            val reply = channel.removeBlockchain(requestBuilder.build())
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}