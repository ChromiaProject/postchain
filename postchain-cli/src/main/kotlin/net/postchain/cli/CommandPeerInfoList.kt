// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.PeerApi
import net.postchain.base.PeerInfo
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.Templater
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig
import java.io.File

class CommandPeerInfoList : CliktCommand(name = "peerinfo-list", help = "List peer information") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        val peerInfos = peerinfoList(nodeConfigFile)

        if (peerInfos.isEmpty()) {
            println("No peerinfo found")
        } else {
            peerInfos.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                    .forEach {
                        println("Peerinfos (${peerInfos.size}):\n$it")
                    }
        }
    }

    private fun peerinfoList(nodeConfigFile: File): Array<PeerInfo> = runStorageCommand(AppConfig.fromPropertiesFile(nodeConfigFile)) { ctx ->
        PeerApi.listPeers(ctx)
    }
}