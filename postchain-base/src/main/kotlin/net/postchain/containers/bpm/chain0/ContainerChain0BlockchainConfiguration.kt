package net.postchain.containers.bpm.chain0

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModuleAware
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.managed.query.GtxModuleQueryRunner

class ContainerChain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
) : BlockchainConfiguration by configuration, ManagedDataSourceAware, GTXModuleAware {

    override val module = configuration.module

    override val dataSource = BaseDirectoryDataSource(
            GtxModuleQueryRunner(configuration, appConfig), appConfig)
}