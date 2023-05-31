// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.StorageBuilder
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandWipeDb : CliktCommand(name = "wipe-db", help = "Wipe Database") {

    private val nodeConfigFile by nodeConfigOption()
    private val recreate by option(help = "Recreate database schemas").flag()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        if (recreate) {
            StorageBuilder.buildStorage(appConfig, wipeDatabase = true).close()
        } else {
            StorageBuilder.wipeDatabase(appConfig)
        }
        println("Database has been wiped successfully")
    }
}
