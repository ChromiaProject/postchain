// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.api.internal.PostchainApi
import net.postchain.base.PeerInfo
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.printCommandInfo
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.crypto.PubKey

class CommandPeerInfoRemove : CliktCommand(name = "peerinfo-remove", help = "Remove peer information") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    private val pubKey by requiredPubkeyOption()

    override fun run() {
        printCommandInfo()

        val removed = peerinfoRemove(nodeConfigFile, pubKey)

        if (removed.isEmpty()) {
            println("No peerinfo has been removed")
        } else {
            removed.mapIndexed(Templater.PeerInfoTemplater::renderPeerInfo)
                .forEach {
                    println("Peerinfo removed (${removed.size}):\n$it")
                }
        }
    }

    private fun peerinfoRemove(nodeConfigFile: String, pubKey: String): Array<PeerInfo> {
        return runStorageCommand(nodeConfigFile) { ctx ->
            PostchainApi.removePeer(ctx, PubKey(pubKey))
        }
    }
}