// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.multiple
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.debugOption
import net.postchain.cli.util.formatOptions
import net.postchain.cli.util.nodeConfigOption

class CommandRunNode : CliktCommand(name = "run-node", help = "Runs node") {

    private val nodeConfigFile by nodeConfigOption()

    private val chainIDs by chainIdOption().multiple(required = true)

    private val debug by debugOption()

    override fun run() {
        println("$commandName will be executed with: ${formatOptions()}")

        CliExecution.runNode(nodeConfigFile, chainIDs, debug)
        println("Postchain node is running")
    }
}