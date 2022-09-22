package net.postchain.managed.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.managed.DirectoryDataSource

class D1BlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule,
        val dataSource: DirectoryDataSource
) : GTXBlockchainConfiguration(configData, module)