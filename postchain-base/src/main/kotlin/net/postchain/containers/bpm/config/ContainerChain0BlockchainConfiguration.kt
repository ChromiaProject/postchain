package net.postchain.containers.bpm.config

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.config.Chain0BlockchainConfiguration
import net.postchain.managed.config.ManagedDataSourceAwareness

class ContainerChain0BlockchainConfiguration(
        configuration: Chain0BlockchainConfiguration,
        module: GTXModule, appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig
) : BlockchainConfiguration by configuration, ManagedDataSourceAwareness by configuration {

    override val dataSource = BaseDirectoryDataSource(module, appConfig, containerNodeConfig)
}