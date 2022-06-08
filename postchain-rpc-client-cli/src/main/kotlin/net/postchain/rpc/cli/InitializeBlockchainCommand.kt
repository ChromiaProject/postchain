package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import com.google.protobuf.ByteString
import io.grpc.StatusRuntimeException
import net.postchain.rpc.cli.util.blockingChannelOption
import net.postchain.server.rpc.InitializeBlockchainRequest
import java.io.File
import kotlin.io.path.extension

class InitializeBlockchainCommand : CliktCommand(help = "Add and start blockchain from configuration") {

    private val channel by blockingChannelOption()

    private val chainId by argument(help = "Chain ID to start").long()

    private val config by argument(help = "Path to config file (xml or gtv format)")
        .convert { File(it) }

    private val override by option("-f", "--override", envvar = "POSTCHAIN_OVERRIDE",  help = "Overrides existing configuration if it exists").flag()

    override fun run() {
        try {
            val requestBuilder = InitializeBlockchainRequest.newBuilder()
                .setChainId(chainId)
                .setOverride(override)

            when (config.toPath().extension) {
                "gtv" -> requestBuilder.gtv = ByteString.copyFrom(config.readBytes())
                "xml" -> requestBuilder.xml = config.readText()
                else -> throw IllegalArgumentException("File must be xml or gtv file")
            }
            val reply = channel.initializeBlockchain(requestBuilder.build())
            println(reply.message)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}