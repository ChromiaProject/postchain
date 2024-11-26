// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.server.cli

import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.PostchainNode
import net.postchain.StorageInitializer
import net.postchain.base.runStorageCommand
import net.postchain.config.app.AppConfig
import net.postchain.server.PostchainNodeProvider

class CommandRunServer : CommandRunServerBase("run-server", "Start postchain server") {

    private val nodeConfigFile by nodeConfigOption()

    private val dumpPid by dumpPidOption()

    private val activeChains by option("--initial-chain-ids", "-c", envvar = "POSTCHAIN_INITIAL_CHAIN_IDS")
            .help("Chain IDs that will be started directly")
            .long().split(",")

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        runStorageCommand(appConfig, allowUpgrade = true) {
            StorageInitializer.setupInitialPeers(appConfig, it)
        }
        val nodeProvider = PostchainNodeProvider(PostchainNode(appConfig, false))
        if (dumpPid) dumpPid()
        runServer(nodeProvider, activeChains)
    }
}
