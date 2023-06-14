package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.CreateImportJobRequest

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
            val requestBuilder = CreateImportJobRequest.newBuilder()
                    .setChainId(chainId)
                    .setConfigurationsFile(configurationsFile)
                    .setBlocksFile(blocksFile)
                    .setIncremental(incremental)
            val reply = channel.createImportJob(requestBuilder.build())
            echo("Import job ${reply.jobId} created")
        } catch (e: StatusRuntimeException) {
            echo("Failed with: ${e.message}")
        }
    }
}
