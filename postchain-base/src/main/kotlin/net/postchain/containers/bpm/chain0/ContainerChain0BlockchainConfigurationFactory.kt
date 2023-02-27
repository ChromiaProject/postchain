package net.postchain.containers.bpm.chain0

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class ContainerChain0BlockchainConfigurationFactory(
        val appConfig: AppConfig,
        val factory: GTXBlockchainConfigurationFactory,
        val containerNodeConfig: ContainerNodeConfig
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(configurationData: Any,
                                             partialContext: BlockchainContext,
                                             blockSigMaker: SigMaker,
                                             eContext: EContext,
                                             cryptoSystem: CryptoSystem): ContainerChain0BlockchainConfiguration {
        val conf = factory.makeBlockchainConfiguration(configurationData, partialContext, blockSigMaker, eContext, cryptoSystem)
        return ContainerChain0BlockchainConfiguration(conf, appConfig, containerNodeConfig)
    }
}
