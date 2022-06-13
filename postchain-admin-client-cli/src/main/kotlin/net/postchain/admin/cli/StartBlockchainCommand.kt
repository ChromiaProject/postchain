package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.server.service.StartBlockchainRequest

class StartBlockchainCommand: CliktCommand(help = "Start blockchain with id") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by option("--chain-id", "-cid", envvar = "POSTCHAIN_CHAIN_ID", help = "Chain ID to start").long().required()

    override fun run() {
        try {
            val request = StartBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .build()

            val reply = channel.startBlockchain(request)
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}