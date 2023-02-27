package net.postchain.containers.bpm.chain0

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.managed.query.QueryRunnerFactory.createChain0QueryRunner

class ContainerChain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        override val module: GTXModule, appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
) : BlockchainConfiguration by configuration, ManagedDataSourceAware, GTXModuleAware {

    override val dataSource = BaseDirectoryDataSource(
            createChain0QueryRunner(module, appConfig), appConfig)
}