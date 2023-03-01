package net.postchain.client.cli

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand

class PostchainClient : CliktCommand(name = "postchain-client") {
    init {
        completionOption()
    }

    override fun run() = Unit
}