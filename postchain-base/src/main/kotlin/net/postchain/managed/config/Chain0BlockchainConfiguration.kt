package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.query.GtxModuleQueryRunner

open class Chain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        val appConfig: AppConfig,
) : ManagedBlockchainConfiguration(
        configuration,
        BaseManagedNodeDataSource(GtxModuleQueryRunner(configuration, appConfig), appConfig)
)
