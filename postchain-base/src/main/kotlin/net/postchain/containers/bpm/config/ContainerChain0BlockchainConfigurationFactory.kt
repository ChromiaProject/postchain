package net.postchain.containers.bpm.config

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory

class ContainerChain0BlockchainConfigurationFactory(
        appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig
) : Chain0BlockchainConfigurationFactory(appConfig) {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val configData = configurationData as BlockchainConfigurationData
        val effectiveBrid = configData.historicBrid ?: configurationData.context.blockchainRID
        return ContainerChain0BlockchainConfiguration(
                configData,
                createGtxModule(effectiveBrid, configurationData),
                appConfig,
                containerNodeConfig
        )
    }
}