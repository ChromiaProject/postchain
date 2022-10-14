// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.api.internal.PostchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo

class CommandRemoveConfiguration : CliktCommand(name = "remove-configuration", help = "Remove configuration at a given height for a blockchain.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val height by option("-h", "--height", help = "Height of configuration to remove").long().required()

    override fun run() {
        printCommandInfo()

        runStorageCommand(nodeConfigFile, chainId) { ctx ->
            val count = PostchainApi.removeConfiguration(ctx, height)
            if (count > 0)
                println("Removed configuration at height $height")
            else
                println("No configurations at $height to remove")
        }
    }
}