package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
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
import net.postchain.admin.cli.util.chainIdOptionNullable
import net.postchain.common.BlockchainRid
import net.postchain.server.grpc.ExportBlockchainRequest

class ExportBlockchainCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(name = "export") { 
    override fun help(context: Context) = "Export a blockchain to file"

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val chainRef by mutuallyExclusiveOptions(
            chainIdOptionNullable(),
            blockchainRidOption(),
            name = "Chain reference"
    ).single().required()

    private val configurationsFile by option("--configurations-file", help = "File to export blockchain configurations to. Note: Only exports local configurations (manual mode). To export managed blockchain configs, use management-console tool.")
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
            echo("Export completed:")
            if (reply.configsExported) {
                echo("  - Configurations: exported to $configurationsFile")
            } else {
                echo("  - Configurations: skipped (managed blockchain)")
            }
            if (blocksFile != null) {
                if (reply.numBlocks > 0) {
                    echo("  - Blocks: ${reply.numBlocks} blocks (${reply.fromHeight}..${reply.upHeight}) exported to $blocksFile")
                } else {
                    echo("  - Blocks: no blocks to export")
                }
            } else {
                echo("  - Blocks: not requested")
            }
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", printError = true)
        }
    }
}