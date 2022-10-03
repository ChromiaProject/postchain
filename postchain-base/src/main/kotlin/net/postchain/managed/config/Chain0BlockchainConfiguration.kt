package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.ManagedNodeDataSource

open class Chain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        val module: GTXModule,
        val appConfig: AppConfig
) : BlockchainConfiguration by configuration, ManagedDataSourceAwareness {

    override val dataSource: ManagedNodeDataSource
         = BaseManagedNodeDataSource(module, appConfig)
}