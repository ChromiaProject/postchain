package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.server.service.StopBlockchainRequest

class StopBlockchainCommand: CliktCommand(help = "Stop blockchain with id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by option("--chain-id", "-cid", envvar = "POSTCHAIN_CHAIN_ID", help = "Chain ID to stop").long().required()

    override fun run() {
        try {
            val request = StopBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.stopBlockchain(request)
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}