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
import net.postchain.config.node.PropertiesNodeConfigurationProvider
import java.io.File

class CommandPeerInfoImport : CliktCommand(name = "import", help = "Import peer information") {

    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        withDbVersionMismatch {
            val imported = peerInfoImport(nodeConfigFile)

            if (imported.isEmpty()) {
                echo("No peer info have been imported")
            } else {
                imported.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                        .forEach {
                            echo("Peer info added (${imported.size}):\n$it")
                        }
            }
        }
    }

    private fun peerInfoImport(nodeConfigFile: File?): Array<PeerInfo> {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        val nodeConfig = PropertiesNodeConfigurationProvider(appConfig).getConfiguration()

        return if (nodeConfig.peerInfoMap.isEmpty()) {
            emptyArray()
        } else {
            runStorageCommand(appConfig) { ctx ->
                PeerApi.addPeers(ctx, nodeConfig.peerInfoMap.values)
            }
        }
    }
}