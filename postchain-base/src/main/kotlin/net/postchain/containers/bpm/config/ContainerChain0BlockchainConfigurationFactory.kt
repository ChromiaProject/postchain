package net.postchain.containers.bpm.config

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class ContainerChain0BlockchainConfigurationFactory(
        val appConfig: AppConfig,
        val factory: GTXBlockchainConfigurationFactory,
        val containerNodeConfig: ContainerNodeConfig
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext): BlockchainConfiguration {
        val conf = factory.makeBlockchainConfiguration(configurationData, eContext) as GTXBlockchainConfiguration
        return ContainerChain0BlockchainConfiguration(conf, conf.module, appConfig, containerNodeConfig)
    }
}