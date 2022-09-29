package net.postchain.managed.bcconfig

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class Chain0BlockchainConfigurationFactory(val appConfig: AppConfig) : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val configData = configurationData as BlockchainConfigurationData
        val effectiveBrid = configData.historicBrid ?: configurationData.context.blockchainRID
        return Chain0BlockchainConfiguration(
                configData,
                createGtxModule(effectiveBrid, configurationData),
                appConfig
        )
    }
}