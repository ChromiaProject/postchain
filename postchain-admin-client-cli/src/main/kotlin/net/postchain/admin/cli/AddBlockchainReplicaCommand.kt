package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.pubkeyOption
import net.postchain.server.grpc.AddBlockchainReplicaRequest

class AddBlockchainReplicaCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "add", help = "Add a peer as replica for a blockchain") {

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val brid by option(
            "-brid", "--blockchain-rid", envvar = "POSTCHAIN_BRID", help = "Blockchain-rid to add replica for"
    ).required()
    private val pubkey by pubkeyOption()

    override fun run() {
        try {
            val request = AddBlockchainReplicaRequest.newBuilder().setBrid(brid).setPubkey(pubkey).build()

            val reply = channel.addBlockchainReplica(request)
            echo(reply)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}