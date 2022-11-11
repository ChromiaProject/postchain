// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import mu.KLogging
import net.postchain.cli.util.TlsOptions
import net.postchain.cli.util.debugOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import net.postchain.server.PostchainServer
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.config.TlsConfig

class CommandRunServer : CliktCommand(name = "run-server", help = "Start postchain server") {

    companion object : KLogging()

    private val nodeConfigFile by nodeConfigOption()

    private val debug by debugOption()

    private val port by option("-p", "--port", envvar = "POSTCHAIN_SERVER_PORT", help = "Port for the server")
            .int().default(PostchainServerConfig.DEFAULT_RPC_SERVER_PORT)

    private val activeChains by option("--initial-chain-ids", "-c", envvar = "POSTCHAIN_INITIAL_CHAIN_IDS")
            .help("Chain IDs that will be started directly")
            .long().split(",")

    private val tlsOptions by TlsOptions().cooccurring()

    override fun run() {
        val serverConfig = tlsOptions?.let {
            PostchainServerConfig(port, TlsConfig(it.certChainFile, it.privateKeyFile))
        } ?: PostchainServerConfig(port)
        PostchainServer(AppConfig.fromPropertiesFile(nodeConfigFile, debug), false, debug, serverConfig)
                .apply {
                    start(activeChains)
                    blockUntilShutdown()
                }
    }
}
