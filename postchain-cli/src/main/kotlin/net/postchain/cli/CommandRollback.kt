// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo

class CommandRollback : CliktCommand(name = "rollback", help = "Rollback configuration to a given height for a blockchain.") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val height by option("-h", "--height", help = "Height of configuration").long().required()

    override fun run() {
        printCommandInfo()

        runStorageCommand(nodeConfigFile, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val count = db.rollbackConfiguration(ctx, height)
            if (count > 0)
                println("Rolled back $count configurations")
            else
                println("No future configurations since $height to roll back")
        }
    }
}