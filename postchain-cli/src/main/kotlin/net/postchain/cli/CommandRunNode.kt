// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.debugOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvFileReader

class CommandRunNode : CliktCommand(name = "run-node", help = "Starts a node with a configuration") {

    private val nodeConfigFile by nodeConfigOption()

    private val blockchainConfigFile by blockchainConfigOption()
    private val chainIDs by chainIdOption().multiple(listOf(0))
    private val override by option(help = "Overrides the configuration if it exists in database").flag()
    private val update by option(help = "Add the configuration on a height higher than current height if blocks are already build on this chain").flag()

    private val debug by debugOption()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile, debug)

        if (blockchainConfigFile != null) {
            require(chainIDs.size == 1) { "Cannot start more than one chain if a blockchain configuration is specified" }

            val blockchainConfig = GtvFileReader.readFile(blockchainConfigFile!!)
            val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)

            runStorageCommand(appConfig, chainIDs[0]) { ctx ->
                val wasInitialized = BlockchainApi.initializeBlockchain(ctx, blockchainRid, override, blockchainConfig)

                if (!wasInitialized && update) {
                    val currentHeight = BlockchainApi.getLastBlockHeight(ctx)
                    BlockchainApi.addConfiguration(ctx, currentHeight + 1, false, blockchainConfig)
                }
            }
        }

        CliExecution.runNode(appConfig, chainIDs)
    }
}
