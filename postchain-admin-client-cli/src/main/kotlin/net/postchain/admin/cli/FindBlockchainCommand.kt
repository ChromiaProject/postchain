package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.FindBlockchainRequest

class FindBlockchainCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "find") { 
    override fun help(context: Context) = "Find blockchain rid from id"

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val request = FindBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()

            val reply = channel.findBlockchain(request)
            echo(reply.brid)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", printError = true)
        }
    }
}