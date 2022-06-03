package net.postchain.rpc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import io.grpc.StatusRuntimeException
import net.postchain.rpc.cli.util.clientArgument
import java.io.File

class AddConfigurationCommand: CliktCommand(help = "Add and start blockchain from configuration") {

    private val client by clientArgument()

    private val chainId by argument(help = "Chain ID to stop").long()

    private val height by argument(help = "Height to add the configuration at").long()

    private val config by argument(help = "Path to config file (xml or gtv format)")
        .convert { File(it) }

    private val override by option("-f", "--override", help = "Overrides existing configuration if it exists").flag()

    override fun run() {
        try {
            client.addConfiguration(chainId, height, config, override)
        } catch (e: StatusRuntimeException) {
            println("Failed with: ${e.message}")
        }
    }
}