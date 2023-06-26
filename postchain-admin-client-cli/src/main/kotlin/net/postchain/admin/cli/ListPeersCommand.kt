package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPeerServiceChannelOption
import net.postchain.server.grpc.ListPeersRequest

class ListPeersCommand : CliktCommand(name = "list", help = "List all peer information in the database") {

    private val channel by blockingPeerServiceChannelOption()

    override fun run() {
        try {
            val request = ListPeersRequest.newBuilder()
                .build()

            val reply = channel.listPeers(request)
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
