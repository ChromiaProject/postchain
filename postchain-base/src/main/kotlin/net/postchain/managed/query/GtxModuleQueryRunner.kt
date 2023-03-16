package net.postchain.managed.query

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXBlockchainConfiguration

open class GtxModuleQueryRunner(val configuration: GTXBlockchainConfiguration, val appConfig: AppConfig) : QueryRunner {

    companion object : KLogging()

    protected val storage = StorageBuilder.buildStorage(appConfig)

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