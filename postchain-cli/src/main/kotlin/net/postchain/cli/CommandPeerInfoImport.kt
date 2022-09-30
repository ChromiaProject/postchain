// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.node.PropertiesNodeConfigurationProvider

class CommandPeerInfoImport : CliktCommand(name = "peerinfo-import", help = "Import peer information") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        printCommandInfo()

        val imported = peerinfoImport(nodeConfigFile)

        if (imported.isEmpty()) {
            println("No peerinfo have been imported")
        } else {
            imported.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                .forEach {
                    println("Peerinfo added (${imported.size}):\n$it")
                }
        }

    }

    private fun peerinfoImport(nodeConfigFile: String): Array<PeerInfo> {
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        val nodeConfig = PropertiesNodeConfigurationProvider(appConfig).getConfiguration()

        return if (nodeConfig.peerInfoMap.isEmpty()) {
            emptyArray()
        } else {
            runStorageCommand(nodeConfigFile) { ctx ->
                val db = DatabaseAccess.of(ctx)
                val imported = mutableListOf<PeerInfo>()

                nodeConfig.peerInfoMap.values.forEach { peerInfo ->
                    val noHostPort = db.findPeerInfo(ctx, peerInfo.host, peerInfo.port, null).isEmpty()
                    val noPubKey = db.findPeerInfo(ctx, null, null, peerInfo.pubKey.toHex()).isEmpty()

                    if (noHostPort && noPubKey) {
                        val added = db.addPeerInfo(
                                ctx, peerInfo.host, peerInfo.port, peerInfo.pubKey.toHex(), peerInfo.lastUpdated)

                        if (added) {
                            imported.add(peerInfo)
                        }
                    }
                }

                imported.toTypedArray()
            }
        }
    }
}