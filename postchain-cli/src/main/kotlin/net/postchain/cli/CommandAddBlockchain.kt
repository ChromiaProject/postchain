// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.validationOption
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvFileReader

class CommandAddBlockchain : CliktCommand(name = "add", help = "Add blockchain") {

    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    private val blockchainConfigFile by blockchainConfigOption().required()

    private val force by forceOption().help("Force the addition of already existed blockchain-rid (by chain-id)")

    private val validation by validationOption()

    override fun run() {
        withDbVersionMismatch {
            val gtv = try {
                GtvFileReader.readFile(blockchainConfigFile)
            } catch (e: Exception) {
                throw PrintMessage("Configuration can not be loaded from the file: ${blockchainConfigFile.path}, an error occurred: ${e.message}", true)
            }

            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            try {
                CliExecution.addBlockchain(appConfig, chainId, gtv, force, validate = validation)
                echo("Blockchain has been added successfully")
            } catch (e: Exception) {
                throw PrintMessage("Can't add blockchain: ${e.message}", true)
            }
        }
    }
}