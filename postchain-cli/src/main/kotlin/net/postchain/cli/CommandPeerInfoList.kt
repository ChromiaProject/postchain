// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.PeerApi
import net.postchain.base.PeerInfo
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.SafeExecutor.withDbVersionMismatch
import net.postchain.cli.util.Templater
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext
import java.io.File

class CommandPeerInfoList : CliktCommand(name = "list", help = "List peer information") {

    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        withDbVersionMismatch {
            val peerInfos = peerInfoList(nodeConfigFile)

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

    private fun peerInfoList(nodeConfigFile: File?): Array<PeerInfo> {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        return runStorageCommand(appConfig) { ctx: AppContext ->
            PeerApi.listPeers(ctx)
        }
    }
}