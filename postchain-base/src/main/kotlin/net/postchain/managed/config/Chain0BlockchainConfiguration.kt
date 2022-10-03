package net.postchain.managed.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.ManagedNodeDataSource

open class Chain0BlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        val appConfig: AppConfig
) : GTXBlockchainConfiguration(configData, module), ManagedDataSourceAwareness {

    override val dataSource: ManagedNodeDataSource
         = BaseManagedNodeDataSource(module, appConfig)
}