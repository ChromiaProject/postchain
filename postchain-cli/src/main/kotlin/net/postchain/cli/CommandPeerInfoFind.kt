// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.api.internal.PeerApi
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.Templater
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.portOption

class CommandPeerInfoFind : CliktCommand(name = "peerinfo-find", help = "Find peerinfo") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val host by hostOption()

    private val port by portOption()

    private val pubKey by option("-pk", "--pubkey", help = "Public key (or substring)")

    override fun run() {
        val peerInfos = runStorageCommand(nodeConfigFile) { ctx ->
            PeerApi.findPeerInfo(ctx, host, port, pubKey)
        }

        if (peerInfos.isEmpty()) {
            println("No peerinfo found")
        } else {
            peerInfos.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                    .forEach {
                        println("Peerinfos (${peerInfos.size}):\n$it")
                    }
        }
    }
}