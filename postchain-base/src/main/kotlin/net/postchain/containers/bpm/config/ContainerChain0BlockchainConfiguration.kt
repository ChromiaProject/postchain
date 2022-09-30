package net.postchain.containers.bpm.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.EContext
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.config.Chain0BlockchainConfiguration

class ContainerChain0BlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig
) : Chain0BlockchainConfiguration(configData, module, appConfig) {

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        dataSource0 = BaseDirectoryDataSource(module, appConfig, containerNodeConfig)
    }

}