// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.heightOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandMustSyncUntil : CliktCommand(name = "must-sync-until", help = "Set this to ensure that chain is not split after a database loss.") {

    private val nodeConfigFile by nodeConfigOption()

    private val blockchainRid by blockchainRidOption()

    private val height by heightOption().help("Node must sync to this height before trying to build new blocks.")
            .required()


    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            val added = CliExecution.setMustSyncUntil(appConfig, blockchainRid,
                    height)
            when {
                added -> println("Command $commandName finished successfully")
                else -> println("Command $commandName failed")
            }
        }
    }
}