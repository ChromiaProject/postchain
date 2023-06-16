// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.server

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import net.postchain.Postchain
import net.postchain.admin.cli.PostchainAdminClientCommand
import net.postchain.server.cli.CommandRunNode
import net.postchain.server.cli.CommandRunNodeAuto
import net.postchain.server.cli.CommandRunServer
import net.postchain.server.cli.CommandRunSubNode

class PostchainServer : NoOpCliktCommand(name = "postchain") {
    init {
        completionOption()
        versionOption(this::class.java.`package`.implementationVersion ?: "(unknown)")
    }
}

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] !in setOf("--generate-completion", "--version")) {
        println("${args[0]} will be executed with: ${args.toList().subList(1, args.size).joinToString(" ", "", "")}")
    }
    return PostchainServer()
            .subcommands(
                    Postchain(),
                    PostchainAdminClientCommand(),
                    CommandRunNode(),
                    CommandRunNodeAuto(),
                    CommandRunServer(),
                    CommandRunSubNode()
            )
            .main(args)
}
