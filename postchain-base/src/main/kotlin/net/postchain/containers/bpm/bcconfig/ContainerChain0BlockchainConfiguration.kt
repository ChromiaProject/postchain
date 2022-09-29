package net.postchain.containers.bpm.bcconfig

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource

class ContainerChain0BlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        val appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig
) : GTXBlockchainConfiguration(configData, module) {

    lateinit var dataSource: DirectoryDataSource

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        dataSource = BaseDirectoryDataSource(module, appConfig, containerNodeConfig)
    }

}