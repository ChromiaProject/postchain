package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPeerServiceChannelOption
import net.postchain.admin.cli.util.pubkeyOption
import net.postchain.server.service.RemovePeerRequest

class RemovePeerCommand : CliktCommand(help = "Remove peer information from database") {

    private val channel by blockingPeerServiceChannelOption()

    private val pubkey by pubkeyOption()

    override fun run() {
        try {
            val request = RemovePeerRequest.newBuilder()
                .setPubkey(pubkey)
                .build()

            val reply = channel.removePeer(request)
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}
