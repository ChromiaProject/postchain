package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPeerServiceChannelOption
import net.postchain.server.service.RemovePeerRequest

class RemovePeerCommand : CliktCommand(help = "Remove peer information from database") {

    private val channel by blockingPeerServiceChannelOption()

    private val pubkey by option("--pubkey", "-k", envvar = "POSTCHAIN_PUBKEY", help = "Public key to add")
        .convert { it.uppercase() }
        .required()

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