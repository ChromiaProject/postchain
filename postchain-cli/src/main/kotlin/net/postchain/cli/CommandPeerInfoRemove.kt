// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.Templater
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext

class CommandPeerInfoRemove : CliktCommand(name = "peerinfo-remove", help = "Remove peer information") {

    private val nodeConfigFile by nodeConfigOption()

    private val pubKey by requiredPubkeyOption()

    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
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
}