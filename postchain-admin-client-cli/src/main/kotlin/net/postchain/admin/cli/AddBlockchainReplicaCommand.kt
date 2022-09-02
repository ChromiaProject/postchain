package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.pubkeyOption
import net.postchain.server.service.AddBlockchainReplicaRequest

class AddBlockchainReplicaCommand : CliktCommand("Add a peer as replica for a blockchain") {

    private val channel by blockingPostchainServiceChannelOption()

    private val brid by option(
        "-brid", "--blockchain-rid", envvar = "POSTCHAIN_BRID", help = "Blockchain-rid to add replica for"
    ).required()
    private val pubkey by pubkeyOption()

    override fun run() {
        try {
            val request = AddBlockchainReplicaRequest.newBuilder().setBrid(brid).setPubkey(pubkey).build()

            val reply = channel.addBlockchainReplica(request)
            println(reply)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}
