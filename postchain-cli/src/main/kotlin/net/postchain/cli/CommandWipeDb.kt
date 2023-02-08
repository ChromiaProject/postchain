// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.StorageBuilder
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandWipeDb : CliktCommand(name = "wipe-db", help = "Wipe Database") {

    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        StorageBuilder.buildStorage(appConfig, true).close()
        println("Database has been wiped successfully")
    }
}
