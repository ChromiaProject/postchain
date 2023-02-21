package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingDebugServiceChannelOption
import net.postchain.server.grpc.DebugRequest

class DebugCommand : CliktCommand(help = "Query for debug information") {

    private val channel by blockingDebugServiceChannelOption()

    override fun run() {
        try {
            val request = DebugRequest.newBuilder()
                .build()

            val reply = channel.debugInfo(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
