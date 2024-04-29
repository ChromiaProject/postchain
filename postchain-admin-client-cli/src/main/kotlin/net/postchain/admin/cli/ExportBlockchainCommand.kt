package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import com.google.protobuf.ByteString
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockchainRidOption
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.admin.cli.util.chainIdOptionNullable
import net.postchain.common.BlockchainRid
import net.postchain.server.grpc.ExportBlockchainRequest

class ExportBlockchainCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "export", help = "Export a blockchain to file") {

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val chainRef by mutuallyExclusiveOptions(
            chainIdOptionNullable(),
            blockchainRidOption(),
            name = "Chain reference"
    ).single().required()

    private val configurationsFile by option("--configurations-file", help = "File to export blockchain configurations to")
            .required()

    private val blocksFile by option("--blocks-file", help = "File to export blocks and transactions to")

    private val fromHeight by option("--from-height",
            help = "Only export configurations and blocks from and including this height (will start from first block by default)")
            .long().default(0L)

    private val upToHeight by option("--up-to-height",
            help = "Only export configurations and blocks up to and including this height (will continue to last block by default)")
            .long().default(Long.MAX_VALUE)

    override fun run() {
        try {
            val chainRef0 = chainRef
            val requestBuilder = ExportBlockchainRequest.newBuilder()
                    .apply {
                        if (chainRef0 is Long) chainId = chainRef0
                        if (chainRef0 is BlockchainRid) blockchainRid = ByteString.copyFrom(chainRef0.data)
                    }
                    .setConfigurationsFile(configurationsFile)
                    .setFromHeight(fromHeight)
                    .setUpToHeight(upToHeight)
                    .let { if (blocksFile != null) it.setBlocksFile(blocksFile) else it }
            val reply = channel.exportBlockchain(requestBuilder.build())
            val message = if (blocksFile != null) {
                if (reply.numBlocks > 0)
                    "Export of ${reply.numBlocks} blocks ${reply.fromHeight}..${reply.upHeight} to $configurationsFile and $blocksFile completed"
                else
                    "No blocks to export to $configurationsFile"
            } else {
                "Export of configurations to $configurationsFile completed"
            }
            echo(message)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}