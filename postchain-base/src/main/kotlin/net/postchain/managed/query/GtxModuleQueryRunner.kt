package net.postchain.managed.query

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule

open class GtxModuleQueryRunner(val chainId: Long, val module: GTXModule, val appConfig: AppConfig) : QueryRunner {

    companion object : KLogging()

    protected val storage = StorageBuilder.buildStorage(appConfig)

    override fun query(name: String, args: Gtv): Gtv {
        val ctx = storage.openReadConnection(chainId)
        return try {
            module.query(ctx, name, args)
        } catch (e: Exception) {
            logger.trace(e) { "An error occurred: ${e.message}" }
            throw e
        } finally {
            storage.closeReadConnection(ctx)
        }
    }
}