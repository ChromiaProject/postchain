package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class Chain0BlockchainConfigurationFactory(val factory: GTXBlockchainConfigurationFactory, val appConfig: AppConfig) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext): Chain0BlockchainConfiguration {
        val configuration = factory.makeBlockchainConfiguration(configurationData, eContext)
        return Chain0BlockchainConfiguration(
                configuration,
                configuration.module,
                appConfig
        )
    }
}