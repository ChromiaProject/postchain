// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.server

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import net.postchain.server.cli.CommandRunNode
import net.postchain.server.cli.CommandRunNodeAuto
import net.postchain.server.cli.CommandRunServer
import net.postchain.server.cli.CommandRunSubNode
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
                    CommandRunNode(),
                    CommandRunNodeAuto(),
                    CommandRunServer(),
                    CommandRunSubNode()
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
