// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.multiple
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.debugOption
import net.postchain.cli.util.nodeConfigOption
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

class CommandRunNode : CliktCommand(name = "run-node", help = "Runs node") {

    private val nodeConfigFile by nodeConfigOption()

    private val chainIDs by chainIdOption().multiple(required = true)

    private val debug by debugOption()

    override fun run() {
        println("run-node will be executed with options: " +
                ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE))

        CliExecution.runNode(nodeConfigFile, chainIDs, debug)
        println("Postchain node is running")
    }
}