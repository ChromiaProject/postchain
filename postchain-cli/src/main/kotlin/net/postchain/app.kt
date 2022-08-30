// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.cli.*
import java.io.File
import java.lang.management.ManagementFactory


class Postchain: CliktCommand(name = "postchain") {
    override fun run() = Unit
}
fun main(args: Array<String>) {
    dumpPid()
    return Postchain()
        .subcommands(
            CommandAddBlockchain(),
            CommandAddConfiguration(),
            CommandHistory(),
            CommandRollback(),
            CommandBlockchainReplicaAdd(),
            CommandBlockchainReplicaRemove(),
            CommandCheckBlockchain(),
            CommandGenerateContainerZfsInitScript(),
            CommandKeygen(),
            CommandMustSyncUntil(),
            CommandPeerInfoAdd(),
            CommandPeerInfoFind(),
            CommandPeerInfoImport(),
            CommandPeerInfoList(),
            CommandPeerInfoRemove(),
            CommandRunNode(),
            CommandRunNodeAuto(),
            CommandRunServer(),
            CommandWaitDb(),
            CommandWipeDb(),
        )
        .main(args)
}

fun dumpPid() {
    val processName = ManagementFactory.getRuntimeMXBean().name
    val pid = processName.split("@")[0]
    File("postchain.pid").writeText(pid)
}
