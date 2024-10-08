package net.postchain.admin.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand

class BlockchainCommand: NoOpCliktCommand() {
    override fun help(context: Context) = "Blockchain related commands"
}
