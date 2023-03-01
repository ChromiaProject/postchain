package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModuleAware
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.query.GtxModuleQueryRunner

open class Chain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        val appConfig: AppConfig,
) : BlockchainConfiguration by configuration, ManagedDataSourceAware, GTXModuleAware {

    override val module = configuration.module

    override val dataSource: ManagedNodeDataSource = BaseManagedNodeDataSource(
            GtxModuleQueryRunner(configuration, appConfig), appConfig)
}