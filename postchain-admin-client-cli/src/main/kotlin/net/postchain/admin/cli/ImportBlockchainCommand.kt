package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.google.protobuf.ByteString
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockchainRidOption
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOptionNullable
import net.postchain.admin.cli.util.toHex
import net.postchain.common.BlockchainRid
import net.postchain.server.grpc.ImportBlockchainRequest

class ImportBlockchainCommand : CliktCommand(name = "import", help = "Import a blockchain from file") {

    private val channel by blockingPostchainServiceChannelOption()

    private val chainRef by mutuallyExclusiveOptions(
            chainIdOptionNullable(),
            blockchainRidOption(),
            name = "Chain reference"
    ).single().required()

    private val configurationsFile by option("--configurations-file", help = "File to import blockchain configurations from")
            .required()

    private val blocksFile by option("--blocks-file", help = "File to import blocks and transactions from")
            .required()

    private val incremental by option("--incremental", help = "Import new configurations and blocks to existing blockchain")
            .flag()

    override fun run() {
        try {
            val chainRef0 = chainRef
            val requestBuilder = ImportBlockchainRequest.newBuilder()
                    .apply {
                        if (chainRef0 is Long) chainId = chainRef0
                        if (chainRef0 is BlockchainRid) blockchainRid = ByteString.copyFrom(chainRef0.data)
                    }
                    .setConfigurationsFile(configurationsFile)
                    .setBlocksFile(blocksFile)
                    .setIncremental(incremental)

            val reply = channel.importBlockchain(requestBuilder.build())
            val message = if (reply.numBlocks > 0)
                "Import of ${reply.numBlocks} blocks ${reply.fromHeight}..${reply.toHeight} to chain ${reply.blockchainRid.toByteArray().toHex()} completed"
            else
                "No blocks to import to chain ${reply.blockchainRid.toByteArray().toHex()}"
            echo(message)
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
