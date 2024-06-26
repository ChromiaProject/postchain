// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.Templater
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.portOption
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext

class CommandPeerInfoFind : CliktCommand(name = "find", help = "Find peer info") {

    private val nodeConfigFile by nodeConfigOption()

    private val host by hostOption()

    private val port by portOption()

    private val pubKey by option("-pk", "--pubkey", help = "Public key (or hex substring)")

    override fun run() {
        withDbVersionMismatch {
            val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
            val peerInfos = runStorageCommand(appConfig) { ctx: AppContext ->
                PeerApi.findPeerInfo(ctx, host, port, pubKey)
            }

            if (peerInfos.isEmpty()) {
                echo("No peer info found")
            } else {
                peerInfos.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                        .forEach {
                            echo("Peer infos (${peerInfos.size}):\n$it")
                        }
            }
        }
    }
}