package net.postchain.managed.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.ManagedNodeDataSource

class ManagedBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        val dataSource: ManagedNodeDataSource
) : GTXBlockchainConfiguration(configData, module)