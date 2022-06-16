// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.cli.util.nodeConfigOption
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

class CommandPeerInfoList : CliktCommand(name = "peerinfo-list", help = "List peer information") {

    // TODO: Eliminate it later or reduce to DbConfig only
    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        println("peerinfo-list will be executed with options: " +
                ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE))

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

    private fun peerinfoList(nodeConfigFile: String): Array<PeerInfo> {
        return runStorageCommand(nodeConfigFile) {
            DatabaseAccess.of(it).findPeerInfo(it, null, null, null)
        }
    }
}