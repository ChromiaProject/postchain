package net.postchain.managed

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule

class ManagedBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        val dataSource: ManagedNodeDataSource
) : GTXBlockchainConfiguration(configData, module)