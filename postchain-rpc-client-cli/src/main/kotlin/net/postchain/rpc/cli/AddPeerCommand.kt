package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.grpc.StatusRuntimeException
import net.postchain.rpc.cli.util.clientArgument

class AddPeerCommand : CliktCommand(help = "Add peer information to database") {

    private val client by clientArgument()

    private val host by argument(help = "Host name to add")
    private val port by argument(help = "port number to add").int()
    private val pubkey by argument(help = "Public key to add")

    private val override by option("-f", "--override", help = "Overrides existing peer if it exists for this public key").flag()

    override fun run() {
        try {
            client.addPeer(host, port, pubkey, override)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}