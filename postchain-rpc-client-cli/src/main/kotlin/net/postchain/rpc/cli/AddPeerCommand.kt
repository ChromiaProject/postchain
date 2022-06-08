package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.grpc.StatusRuntimeException
import net.postchain.rpc.cli.util.blockingChannelOption
import net.postchain.server.rpc.AddPeerRequest

class AddPeerCommand : CliktCommand(help = "Add peer information to database") {

    private val channel by blockingChannelOption()

    private val host by option("--host", envvar = "POSTCHAIN_HOST", help = "Host name to add").required()
    private val port by option("--port", envvar = "POSTCHAIN_PORT", help = "port number to add").int().required()
    private val pubkey by option("--pubkey", "-k", envvar = "POSTCHAIN_PUBKEY", help = "Public key to add").required()

    private val override by option("-f", "--override", help = "Overrides existing peer if it exists for this public key").flag()

    override fun run() {
        try {
            val request = AddPeerRequest.newBuilder()
                .setHost(host)
                .setPort(port)
                .setPubkey(pubkey)
                .setOverride(override)
                .build()

            val reply = channel.addPeer(request)
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}