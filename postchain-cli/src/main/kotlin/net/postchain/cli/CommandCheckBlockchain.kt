// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo

class CommandCheckBlockchain : CliktCommand(name = "check-blockchain", help = "Checks Blockchain") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        printCommandInfo()

        CliExecution.checkBlockchain(nodeConfigFile, chainId, blockchainRID.toHex())
        println("Okay")
    }
}