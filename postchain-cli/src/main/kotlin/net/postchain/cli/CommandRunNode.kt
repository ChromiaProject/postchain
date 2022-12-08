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
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFileReader

class CommandRunNode : CliktCommand(name = "run-node", help = "Starts a node with a configuration") {

    private val nodeConfigFile by nodeConfigOption()

    private val blockchainConfigFile by blockchainConfigOption()
    private val chainIDs by chainIdOption().multiple(listOf(0))
    private val override by option(help = "Overrides the configuration if it exists in database").flag()
    private val update by option(help = "Add the configuration on a height higher than current height if blocks are already build on this chain").flag()

    private val debug by debugOption()

    override fun run() {
        if (blockchainConfigFile != null) {
            require(chainIDs.size == 1) { "Cannot start more than one chain if a blockchain configuration is specified" }
            runStorageCommand(nodeConfigFile, chainIDs[0]) {
                val current = try { BlockchainApi.getLastBlockHeight(it) } catch (e: Exception) { -1 }
                val bcConfig = GtvFileReader.readFile(blockchainConfigFile!!)
                when {
                    current <= 0 || override -> initializeBlockchain(it, bcConfig)
                    update -> BlockchainApi.addConfiguration(it, current + 1, false, bcConfig)
                    else -> {}
                }
            }
        }
        CliExecution.runNode(nodeConfigFile, chainIDs, debug)
    }

    private fun initializeBlockchain(eContext: EContext, config: Gtv) {
        val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(config, AppConfig.fromPropertiesFile(nodeConfigFile).cryptoSystem)
        BlockchainApi.initializeBlockchain(eContext, brid, override, config)
    }
}
