package net.postchain.containers.bpm.chain0

import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class ContainerChain0BlockchainConfigurationFactory(
        val appConfig: AppConfig,
        val factory: GTXBlockchainConfigurationFactory,
        val containerNodeConfig: ContainerNodeConfig,
        val storage: Storage
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ): ContainerChain0BlockchainConfiguration {
        val conf = factory.makeBlockchainConfiguration(
                configurationData, partialContext, blockSigMaker, eContext, cryptoSystem, blockchainConfigurationOptions)
        return ContainerChain0BlockchainConfiguration(conf, appConfig, containerNodeConfig, storage)
    }
}
