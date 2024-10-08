package net.postchain.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand

class BlockchainCommand: NoOpCliktCommand() {
    override fun help(context: Context) = "Commands related to adding/removing blockchain configurations"
}
