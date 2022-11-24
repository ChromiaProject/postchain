// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.PeerApi
import net.postchain.base.PeerInfo
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.pubkeyOption
import net.postchain.config.app.AppConfig
import java.io.File

class CommandPeerInfoFind : CliktCommand(name = "peerinfo-find", help = "Find peerinfo") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val host by hostOption()

    private val port by portOption()

    private val pubKey by pubkeyOption()

    override fun run() {
        val peerInfos = peerinfoFind(nodeConfigFile, host, port, pubKey)

        if (peerInfos.isEmpty()) {
            println("No peerinfo found")
        } else {
            peerInfos.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                    .forEach {
                        println("Peerinfos (${peerInfos.size}):\n$it")
                    }
        }
    }

    private fun peerinfoFind(nodeConfigFile: File, host: String?, port: Int?, pubKey: String?): Array<PeerInfo> =
            runStorageCommand(AppConfig.fromPropertiesFile(nodeConfigFile)) { ctx ->
                PeerApi.findPeerInfo(ctx, host, port, pubKey)
            }
}