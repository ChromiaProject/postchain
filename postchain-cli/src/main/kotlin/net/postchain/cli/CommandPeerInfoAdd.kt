// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.config.app.AppConfig

class CommandPeerInfoAdd : CliktCommand(name = "peerinfo-add", help = "Add peer information to database") {

    private val nodeConfigFile by nodeConfigOption()

    private val host by hostOption().required()

    private val port by portOption().required()

    private val pubKey by requiredPubkeyOption()

    private val force by forceOption().help("Force the addition of peerinfo which already exists with the same host:port")

    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            val added = CliExecution.peerinfoAdd(appConfig, host, port, pubKey, force)
            when {
                added -> println("Peerinfo has been added successfully")
                else -> println("Peerinfo hasn't been added")
            }
        }
    }
}