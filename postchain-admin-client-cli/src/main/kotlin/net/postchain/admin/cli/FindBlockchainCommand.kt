package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.FindBlockchainRequest

class FindBlockchainCommand : CliktCommand(help = "Find blockchain rid from id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val request = FindBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.findBlockchain(request)
            echo(reply.brid)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
