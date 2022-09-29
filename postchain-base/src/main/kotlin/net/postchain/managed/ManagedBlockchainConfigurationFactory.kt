package net.postchain.managed

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class ManagedBlockchainConfigurationFactory(val managedDataSource: ManagedNodeDataSource) : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val configData = configurationData as BlockchainConfigurationData
        val effectiveBrid = configData.historicBrid ?: configurationData.context.blockchainRID
        return ManagedBlockchainConfiguration(
                configData,
                createGtxModule(effectiveBrid, configurationData),
                managedDataSource
        )
    }

}