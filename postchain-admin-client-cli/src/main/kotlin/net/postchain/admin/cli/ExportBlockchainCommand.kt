package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.ExportBlockchainRequest

class ExportBlockchainCommand : CliktCommand(help = "Export a blockchain to file") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainId by chainIdOption()

    private val configurationsFile by option("--configurations-file", help = "File to import blockchain configurations from")
            .required()

    private val blocksFile by option("--blocks-file", help = "File to import blocks and transactions from")
            .required()

    private val fromHeight by option("--from-height",
            help = "Only export configurations and blocks from and including this height (will start from first block by default)")
            .long().default(0L)

    private val upToHeight by option("--up-to-height",
            help = "Only export configurations and blocks up to and including this height (will continue to last block by default)")
            .long().default(Long.MAX_VALUE)

    override fun run() {
        try {
            val requestBuilder = ExportBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .setConfigurationsFile(configurationsFile)
                    .setBlocksFile(blocksFile)
                    .setFromHeight(fromHeight)
                    .setUpToHeight(upToHeight)
            val reply = channel.exportBlockchain(requestBuilder.build())
            val message = if (reply.numBlocks > 0)
                "Export of ${reply.numBlocks} blocks ${reply.fromHeight}..${reply.upHeight} to $configurationsFile and $blocksFile completed"
            else
                "No blocks to export to $configurationsFile and $blocksFile"
            echo(message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
