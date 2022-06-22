// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.*
import mu.KLogging
import net.postchain.cli.util.*
import net.postchain.config.app.AppConfig
import net.postchain.server.PostchainServer
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.config.SslConfig

class CommandRunServer : CliktCommand(name = "run-server", help = "Start postchain server") {

    companion object : KLogging()

    private val nodeConfigFile by nodeConfigOption()

    private val debug by debugOption()

    private val port by portOption().help("Port for the server").default(50051)

    private val sslOptions by SslOptions().cooccurring()

    override fun run() {
        printCommandInfo()

        val serverConfig = sslOptions?.let {
            PostchainServerConfig(port, SslConfig(it.certChainFile, it.privateKeyFile))
        } ?: PostchainServerConfig(port)
        PostchainServer(AppConfig.fromPropertiesFile(nodeConfigFile), false, debug, serverConfig)
            .apply {
                start()
                blockUntilShutdown()
            }
    }
}
