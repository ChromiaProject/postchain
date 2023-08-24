package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPeerServiceChannelOption
import net.postchain.admin.cli.util.pubkeyOption
import net.postchain.server.grpc.RemovePeerRequest

class RemovePeerCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "list", help = "Remove peer information from database") {

    private val channel by blockingPeerServiceChannelOption(channelFactory)

    private val pubkey by pubkeyOption()

    override fun run() {
        try {
            val request = RemovePeerRequest.newBuilder()
                    .setPubkey(pubkey)
                    .build()

            val reply = channel.removePeer(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}