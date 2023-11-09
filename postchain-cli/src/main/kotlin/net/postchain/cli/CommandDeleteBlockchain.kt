// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig

class CommandDeleteBlockchain : CliktCommand(name = "delete", help = "Delete blockchain") {

    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            try {
                runStorageCommand(appConfig, chainId) {
                    val dependentChains = BlockchainApi.getDependentChains(it)
                    if (dependentChains.isNotEmpty()) {
                        throw UserMistake("Blockchain may not be deleted due to the following dependent chains: ${dependentChains.joinToString(", ")}")
                    }

                    BlockchainApi.deleteBlockchain(it)
                }
                echo("OK: Blockchain was deleted")
            } catch (e: UserMistake) {
                throw PrintMessage(e.message ?: "User error")
            } catch (e: Exception) {
                throw PrintMessage("Can't delete blockchain: ${e.message}", true)
            }
        }
    }
}