package net.postchain.cli.util

import net.postchain.cli.CliExecution
import java.io.File

object SafeExecutor {

    fun runOnChain(nodeConfigFile: File, chainId: Long, action: () -> Unit) {
        val brid = CliExecution.findBlockchainRid(nodeConfigFile, chainId)
        if (brid != null) {
            action()
        } else {
            println("Can't find blockchain by chainId: $chainId")
        }
    }

}