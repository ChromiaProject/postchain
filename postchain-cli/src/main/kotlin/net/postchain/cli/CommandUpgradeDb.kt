// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.StorageBuilder
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandUpgradeDb : CliktCommand(name = commandName, help = "Upgrade Database") {

    companion object {
        const val commandName = "upgrade-db"
    }

    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        confirm("Are you sure you want to upgrade database?", default = false, abort = true)
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        StorageBuilder.buildStorage(appConfig, allowUpgrade = true).use {
            echo("Database has been upgraded successfully")
        }
    }
}
