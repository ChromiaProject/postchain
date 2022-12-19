// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvFileReader

class CommandAddBlockchain : CliktCommand(name = "add-blockchain", help = "Add blockchain") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption().required()

    private val chainId by chainIdOption().required()

    private val blockchainConfigFile by blockchainConfigOption().required()

    private val force by forceOption().help("Force the addition of already existed blockchain-rid (by chain-id)")


    override fun run() {
        val gtv = try {
            GtvFileReader.readFile(blockchainConfigFile)
        } catch (e: Exception) {
            println("Configuration can not be loaded from the file: ${blockchainConfigFile.path}, an error occurred: ${e.message}")
            return
        }

        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        CliExecution.addBlockchain(appConfig, chainId, gtv, force)
        println("Configuration has been added successfully")
    }
}