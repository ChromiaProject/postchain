// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.Templater
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext

class CommandPeerInfoRemove : CliktCommand(name = "peerinfo-remove", help = "Remove peer information") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption().required()

    private val pubKey by requiredPubkeyOption()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        val removed = runStorageCommand(appConfig) { ctx: AppContext ->
            PeerApi.removePeer(ctx, pubKey)
        }

        if (removed.isEmpty()) {
            println("No peerinfo has been removed")
        } else {
            removed.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                    .forEach {
                        println("Peerinfo removed (${removed.size}):\n$it")
                    }
        }
    }
}