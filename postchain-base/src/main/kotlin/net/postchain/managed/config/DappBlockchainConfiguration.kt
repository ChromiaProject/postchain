package net.postchain.managed.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.ManagedNodeDataSource

open class DappBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        override val dataSource: ManagedNodeDataSource
) : GTXBlockchainConfiguration(configData, module), ManagedDataSourceAwareness