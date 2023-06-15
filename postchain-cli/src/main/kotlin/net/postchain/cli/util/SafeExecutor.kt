package net.postchain.cli.util

import com.github.ajalt.clikt.core.CliktError
import net.postchain.base.data.DbVersionDowngradeDisallowedException
import net.postchain.base.data.DbVersionUpgradeDisallowedException
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

    fun <T> withDbVersionMismatch(action: () -> T): T = try {
        action()
    } catch (e: DbVersionDowngradeDisallowedException) {
        throw CliktError("Error: ${e.message}. Update postchain-cli.")
    } catch (e: DbVersionUpgradeDisallowedException) {
        throw CliktError("Error: ${e.message}. Upgrade database by running node.")
    }
}
