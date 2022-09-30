package net.postchain.managed.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.ManagedNodeDataSource

class DappBlockchainConfigurationFactory(val dataSource: ManagedNodeDataSource) : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val configData = configurationData as BlockchainConfigurationData
        val effectiveBrid = configData.historicBrid ?: configurationData.context.blockchainRID
        return DappBlockchainConfiguration(
                configData,
                createGtxModule(effectiveBrid, configurationData),
                dataSource
        )
    }
}