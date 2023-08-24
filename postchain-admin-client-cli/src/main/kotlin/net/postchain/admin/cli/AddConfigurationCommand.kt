package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import com.google.protobuf.ByteString
import io.grpc.StatusRuntimeException
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingPostchainServiceChannelOption
import net.postchain.admin.cli.util.chainIdOption
import net.postchain.server.grpc.AddConfigurationRequest
import kotlin.io.path.extension

class AddConfigurationCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(help = "Add and start blockchain from configuration") {

    private val channel by blockingPostchainServiceChannelOption(channelFactory)

    private val chainId by chainIdOption()

    private val height by option(
            "--height", "-h", envvar = "POSTCHAIN_HEIGHT", help = "Height to add the configuration at"
    )
            .long().required()

    private val config by option(
            "--blockchain-config",
            "-bc",
            envvar = "POSTCHAIN_BLOCKCHAIN_CONFIG",
            help = "Path to config file (xml or gtv format)"
    )
            .file()
            .required()

    private val override by option("-f", "--override", help = "Overrides existing configuration if it exists").flag()

    override fun run() {
        try {
            val requestBuilder = AddConfigurationRequest.newBuilder()
                    .setChainId(chainId)
                    .setHeight(height)
                    .setOverride(override)
            when (config.toPath().extension) {
                "gtv" -> requestBuilder.gtv = ByteString.copyFrom(config.readBytes())
                "xml" -> requestBuilder.xml = config.readText()
                else -> throw IllegalArgumentException("File must be xml or gtv file")
            }
            val reply = channel.addConfiguration(requestBuilder.build())
            echo(reply.message)
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}