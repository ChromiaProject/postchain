package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.common.toHex
import net.postchain.server.grpc.ImportBlockchainRequest

class ImportBlockchainCommand : CliktCommand(help = "Import a blockchain from file") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    private val configurationsFile by option("--configurations-file", help = "File to import blockchain configurations from")
            .required()

    private val blocksFile by option("--blocks-file", help = "File to import blocks and transactions from")
            .required()

    private val incremental by option("--incremental", help = "Import new configurations and blocks to existing blockchain")
            .flag()

    override fun run() {
        try {
            val requestBuilder = ImportBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .setConfigurationsFile(configurationsFile)
                    .setBlocksFile(blocksFile)
                    .setIncremental(incremental)
            val reply = channel.importBlockchain(requestBuilder.build())
            val message = if (reply.numBlocks > 0)
                "Import of ${reply.numBlocks} blocks ${reply.fromHeight}..${reply.toHeight} to chain $chainId with bc-rid ${reply.blockchainRid.toByteArray().toHex()} completed"
            else
                "No blocks to import to chain $chainId with bc-rid ${reply.blockchainRid.toByteArray().toHex()}"
            echo(message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
