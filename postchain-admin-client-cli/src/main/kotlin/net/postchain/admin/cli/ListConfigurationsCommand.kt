package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.ListConfigurationsRequest

class ListConfigurationsCommand : CliktCommand(help = "List blockchain configurations") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    override fun run() {
        try {
            val requestBuilder = ListConfigurationsRequest.newBuilder()
                    .setChainId(chainId)
            val reply = channel.listConfigurations(requestBuilder.build())
            echo("Height")
            echo("------")
            reply.heightList.forEach(::echo)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
