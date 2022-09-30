package net.postchain.managed.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.ManagedNodeDataSource

open class Chain0BlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        val appConfig: AppConfig
) : GTXBlockchainConfiguration(configData, module), ManagedDataSourceAwareness {

    protected lateinit var dataSource0: ManagedNodeDataSource

    override val dataSource: ManagedNodeDataSource
        get() = dataSource0

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        dataSource0 = BaseManagedNodeDataSource(module, appConfig)
    }
}