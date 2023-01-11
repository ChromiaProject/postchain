package net.postchain.cli.util

import net.postchain.cli.CliExecution
import net.postchain.config.app.AppConfig

object SafeExecutor {

    fun runOnChain(appConfig: AppConfig, chainId: Long, action: () -> Unit) {
        val brid = CliExecution.findBlockchainRid(appConfig, chainId)
        if (brid != null) {
            action()
        } else {
            println("Can't find blockchain by chainId: $chainId")
        }
    }

}