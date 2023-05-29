// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.StorageBuilder
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.withWriteConnection
import net.postchain.cli.util.chainIdOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig

class CommandDeleteBlockchain : CliktCommand(name = "delete-blockchain", help = "Delete blockchain") {

    private val nodeConfigFile by nodeConfigOption()

    private val chainId by chainIdOption().required()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        try {
            withWriteConnection(StorageBuilder.buildStorage(appConfig), chainId) { ctx ->
                BlockchainApi.deleteBlockchain(ctx)
                true
            }
            println("OK: Blockchain was deleted")
        } catch (e: UserMistake) {
            println(e.message)
        } catch (e: Exception) {
            println("Can't delete blockchain: ${e.message}")
        }
    }
}