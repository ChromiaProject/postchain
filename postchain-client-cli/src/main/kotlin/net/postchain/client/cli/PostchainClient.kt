package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

class PostchainClient : CliktCommand(name = "postchain-client") {

    val configFile by option("-c", "--config", help = "Configuration *.properties of node and blockchain")
            .file(mustExist = true, mustBeReadable = true)
            .required()

    override fun run() = Unit
}