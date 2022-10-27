package net.postchain.containers.bpm.config

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAwareness
import net.postchain.managed.TestmodeDirectoryDataSource
import net.postchain.managed.config.ManagedDataSourceAwareness
import net.postchain.managed.query.QueryRunnerFactory.createChain0QueryRunner

class ContainerChain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        override val module: GTXModule, appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
) : BlockchainConfiguration by configuration, ManagedDataSourceAwareness, GTXModuleAwareness {

    override val dataSource = TestmodeDirectoryDataSource(
            createChain0QueryRunner(module, appConfig), appConfig, containerNodeConfig)
}