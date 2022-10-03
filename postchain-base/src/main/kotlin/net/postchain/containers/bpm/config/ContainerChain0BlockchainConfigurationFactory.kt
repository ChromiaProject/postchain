package net.postchain.containers.bpm.config

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory

class ContainerChain0BlockchainConfigurationFactory(
        val appConfig: AppConfig,
        val factory: Chain0BlockchainConfigurationFactory,
        val containerNodeConfig: ContainerNodeConfig
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext): ContainerChain0BlockchainConfiguration {
        val conf = factory.makeBlockchainConfiguration(configurationData, eContext)
        return ContainerChain0BlockchainConfiguration(conf, conf.module, appConfig, containerNodeConfig)
    }
}