package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.StartBlockchainRequest

class StartBlockchainCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "start") { 
    override fun help(context: Context) = "Start blockchain with id"

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val request = StartBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()

            val reply = channel.startBlockchain(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", printError = true)
        }
    }
}