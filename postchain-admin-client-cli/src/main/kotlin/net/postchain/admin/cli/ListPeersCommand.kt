package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPeerServiceChannelOption
import net.postchain.server.grpc.ListPeersRequest

class ListPeersCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "list", help = "List all peer information in the database") {

    private val channel by blockingPeerServiceChannelOption(channelFactory)

    override fun run() {
        try {
            val request = ListPeersRequest.newBuilder().build()
            val reply = channel.listPeers(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}