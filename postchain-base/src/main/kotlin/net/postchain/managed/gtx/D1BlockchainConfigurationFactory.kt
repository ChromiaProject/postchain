package net.postchain.managed.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.DirectoryDataSource

class D1BlockchainConfigurationFactory(val directoryDataSource: DirectoryDataSource) : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val configData = configurationData as BlockchainConfigurationData
        val effectiveBrid = configData.historicBrid ?: configurationData.context.blockchainRID
        return D1BlockchainConfiguration(
                configData,
                createGtxModule(effectiveBrid, configurationData),
                directoryDataSource
        )
    }

}