package net.postchain.client.cli

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.versionOption

class PostchainClient : CliktCommand(name = "postchain-client") {
    init {
        completionOption()
        versionOption(this::class.java.`package`.implementationVersion ?: "(unknown)")
    }

    override fun run() = Unit
}