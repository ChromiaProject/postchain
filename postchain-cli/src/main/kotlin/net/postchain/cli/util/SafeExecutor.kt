package net.postchain.cli.util

import net.postchain.cli.CliExecution
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig

object SafeExecutor {

    fun runOnChain(appConfig: AppConfig, chainId: Long, action: (BlockchainRid) -> Unit) {
        val brid = CliExecution.findBlockchainRid(appConfig, chainId)
        if (brid != null) {
            action(brid)
        } else {
            println("Can't find blockchain by chainId: $chainId")
        }
    }

}
