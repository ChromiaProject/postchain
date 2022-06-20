package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.service.FindBlockchainRequest

class FindBlockchainCommand : CliktCommand(help = "Find blockchain rid from id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val request = FindBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.findBlockchain(request)
            println(reply.brid)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}
