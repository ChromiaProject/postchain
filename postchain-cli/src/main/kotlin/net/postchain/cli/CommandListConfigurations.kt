// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.SafeExecutor.runIfChainExists
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption

class CommandListConfigurations : CliktCommand(name = "list-configurations", help = "Lists configurations for a blockchain.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    override fun run() {
        runIfChainExists(nodeConfigFile, chainId) {
            println("Height")
            println("------")
            CliExecution.listConfigurations(nodeConfigFile, chainId).forEach(::println)
        }
    }
}