package net.postchain.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand

class ReplicaCommand: NoOpCliktCommand() {
    override fun help(context: Context) = "Command for adding/removing replication of blockchains"
}
