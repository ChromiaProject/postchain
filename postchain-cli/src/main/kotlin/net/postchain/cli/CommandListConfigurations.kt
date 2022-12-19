// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.SafeExecutor.runOnChain
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandListConfigurations : CliktCommand(name = "list-configurations", help = "Lists configurations for a blockchain.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption().required()

    private val chainId by chainIdOption().required()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        runOnChain(appConfig, chainId) {
            println("Height")
            println("------")
            CliExecution.listConfigurations(appConfig, chainId).forEach(::println)
        }
    }
}