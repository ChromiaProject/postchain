package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPeerServiceChannelOption
import net.postchain.server.service.ListPeersRequest

class ListPeersCommand : CliktCommand(help = "List all peer information in the database") {

    private val channel by blockingPeerServiceChannelOption()

    override fun run() {
        try {
            val request = ListPeersRequest.newBuilder()
                .build()

            val reply = channel.listPeers(request)
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}