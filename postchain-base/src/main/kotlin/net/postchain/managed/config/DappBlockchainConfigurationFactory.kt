package net.postchain.managed.config

import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.ManagedNodeDataSource

open class DappBlockchainConfigurationFactory(
        val factory: GTXBlockchainConfigurationFactory,
        val dataSource: ManagedNodeDataSource
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext): DappBlockchainConfiguration {
        val conf = factory.makeBlockchainConfiguration(configurationData, eContext)
        return DappBlockchainConfiguration(conf, dataSource)
    }
}