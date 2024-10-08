package net.postchain.admin.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand

class PeerCommand: NoOpCliktCommand() {
    override fun help(context: Context) = "Add or remove peer information"
}
