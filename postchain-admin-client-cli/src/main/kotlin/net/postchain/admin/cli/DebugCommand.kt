package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingDebugServiceChannelOption
import net.postchain.server.grpc.DebugRequest

class DebugCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(help = "Query for debug information") {

    private val channel by blockingDebugServiceChannelOption(channelFactory)

    override fun run() {
        try {
            val request = DebugRequest.newBuilder().build()
            val reply = channel.debugInfo(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}