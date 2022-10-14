package net.postchain.managed.query

import net.postchain.config.app.AppConfig
import net.postchain.gtx.GTXModule

object QueryRunnerFactory {

    fun createChain0QueryRunner(module: GTXModule, appConfig: AppConfig): QueryRunner =
            GtxModuleQueryRunner(0L, module, appConfig)

    fun createQueryRunner(chainId: Long, module: GTXModule, appConfig: AppConfig): QueryRunner =
            GtxModuleQueryRunner(chainId, module, appConfig)

}