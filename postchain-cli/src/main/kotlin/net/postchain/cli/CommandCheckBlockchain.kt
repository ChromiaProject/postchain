// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.SafeExecutor.runOnChain
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig

class CommandCheckBlockchain : CliktCommand(name = "check-blockchain", help = "Checks Blockchain") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption().required()

    private val chainId by chainIdOption().required()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        runOnChain(appConfig, chainId) {
            try {
                CliExecution.checkBlockchain(appConfig, chainId, blockchainRID.toHex())
                println("OK: blockchain with specified chainId and blockchainRid exists")
            } catch (e: UserMistake) {
                println(e.message)
            } catch (e: Exception) {
                println("Can't check blockchain: ${e.message}")
            }
        }
    }
}