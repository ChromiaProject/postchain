package net.postchain.containers.bpm.chain0

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.config.ManagedBlockchainConfiguration
import net.postchain.managed.query.GtxModuleQueryRunner

class ContainerChain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
) : ManagedBlockchainConfiguration(
        configuration,
        BaseManagedNodeDataSource(GtxModuleQueryRunner(configuration, appConfig), appConfig)
)
