package net.postchain.managed.query

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.core.Storage
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXBlockchainConfiguration

open class GtxModuleQueryRunner(val configuration: GTXBlockchainConfiguration,
                                val appConfig: AppConfig,
                                val storage: Storage
) : QueryRunner {

    companion object : KLogging()

    override fun query(name: String, args: Gtv): Gtv {
        val ctx = storage.openReadConnection(configuration.chainID)
        return try {
            configuration.module.query(ctx, name, args)
        } catch (e: Exception) {
            logger.trace(e) { "An error occurred: ${e.message}" }
            throw e
        } finally {
            storage.closeReadConnection(ctx)
        }
    }
}