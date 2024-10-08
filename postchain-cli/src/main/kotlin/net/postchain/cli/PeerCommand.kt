package net.postchain.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand

class PeerCommand: NoOpCliktCommand() {
    override fun help(context: Context) = "Commands related to peer information"
}