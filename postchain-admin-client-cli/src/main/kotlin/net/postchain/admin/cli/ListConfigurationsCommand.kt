package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.ListConfigurationsRequest

class ListConfigurationsCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(help = "List blockchain configurations") {

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val requestBuilder = ListConfigurationsRequest.newBuilder()
                    .setChainId(chainId)
            val reply = channel.listConfigurations(requestBuilder.build())
            echo("Height")
            echo("------")
            reply.heightList.forEach(::echo)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}