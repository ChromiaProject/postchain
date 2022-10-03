package net.postchain.containers.bpm.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.config.ManagedDataSourceAwareness

class ContainerChain0BlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig
) : GTXBlockchainConfiguration(configData, module), ManagedDataSourceAwareness {

    override val dataSource = BaseDirectoryDataSource(module, appConfig, containerNodeConfig)
}