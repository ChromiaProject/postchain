package net.postchain.managed.config

import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class Chain0BlockchainConfigurationFactory(
        val factory: GTXBlockchainConfigurationFactory,
        val appConfig: AppConfig,
        val storage: Storage
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ): Chain0BlockchainConfiguration {
        val configuration = factory.makeBlockchainConfiguration(
                configurationData, partialContext, blockSigMaker, eContext, cryptoSystem, blockchainConfigurationOptions)
        return Chain0BlockchainConfiguration(configuration, appConfig, storage)
    }
}