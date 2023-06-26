package net.postchain.containers.bpm.chain0

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Storage
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.config.ManagedBlockchainConfiguration
import net.postchain.managed.query.GtxModuleQueryRunner

class ContainerChain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
        storage: Storage
) : ManagedBlockchainConfiguration(
        configuration,
        BaseDirectoryDataSource(GtxModuleQueryRunner(configuration, appConfig, storage), appConfig)
)
