// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import net.postchain.cli.CommandAddBlockchain
import net.postchain.cli.CommandAddConfiguration
import net.postchain.cli.CommandBlockchainReplicaAdd
import net.postchain.cli.CommandBlockchainReplicaRemove
import net.postchain.cli.CommandCheckBlockchain
import net.postchain.cli.CommandGenerateContainerZfsInitScript
import net.postchain.cli.CommandListConfigurations
import net.postchain.cli.CommandMustSyncUntil
import net.postchain.cli.CommandPeerInfoAdd
import net.postchain.cli.CommandPeerInfoFind
import net.postchain.cli.CommandPeerInfoImport
import net.postchain.cli.CommandPeerInfoList
import net.postchain.cli.CommandPeerInfoRemove
import net.postchain.cli.CommandRemoveConfiguration
import net.postchain.cli.CommandRunNode
import net.postchain.cli.CommandRunNodeAuto
import net.postchain.cli.CommandRunServer
import net.postchain.cli.CommandRunSubNode
import net.postchain.cli.CommandWaitDb
import net.postchain.cli.CommandWipeDb
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory


class Postchain : CliktCommand(name = "postchain") {
    init {
        completionOption()
        versionOption(this::class.java.`package`.implementationVersion ?: "(unknown)")
    }

    override fun run() = Unit
}

fun main(args: Array<String>) {
    dumpPid()
    if (args.isNotEmpty() && args[0] !in setOf("--generate-completion", "--version")) {
        println("${args[0]} will be executed with: ${args.toList().subList(1, args.size).joinToString(" ", "", "")}")
    }
    return Postchain()
            .subcommands(
                    CommandAddBlockchain(),
                    CommandAddConfiguration(),
                    CommandListConfigurations(),
                    CommandRemoveConfiguration(),
                    CommandBlockchainReplicaAdd(),
                    CommandBlockchainReplicaRemove(),
                    CommandCheckBlockchain(),
                    CommandGenerateContainerZfsInitScript(),
                    CommandMustSyncUntil(),
                    CommandPeerInfoAdd(),
                    CommandPeerInfoFind(),
                    CommandPeerInfoImport(),
                    CommandPeerInfoList(),
                    CommandPeerInfoRemove(),
                    CommandRunNode(),
                    CommandRunNodeAuto(),
                    CommandRunServer(),
                    CommandRunSubNode(),
                    CommandWaitDb(),
                    CommandWipeDb(),
            )
            .main(args)
}

fun dumpPid() {
    val processName = ManagementFactory.getRuntimeMXBean().name
    val pid = processName.split("@")[0]
    try {
        File("postchain.pid").writeText(pid)
    } catch (e: IOException) { // might fail due to permission error in containers
        println("Postchain PID: $pid")
    }
}
