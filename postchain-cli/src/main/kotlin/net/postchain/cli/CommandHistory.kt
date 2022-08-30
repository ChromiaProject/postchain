// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo

class CommandHistory : CliktCommand(name = "history", help = "Shows configuration history for a blockchain.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    override fun run() {
        printCommandInfo()

        println("Height")
        println("------")
        val configurations = CliExecution.listConfigurations(nodeConfigFile, chainId)
        configurations.forEach { println(it) }
    }
}