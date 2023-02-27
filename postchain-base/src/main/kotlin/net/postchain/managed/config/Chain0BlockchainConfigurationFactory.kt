package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class Chain0BlockchainConfigurationFactory(val factory: GTXBlockchainConfigurationFactory, val appConfig: AppConfig) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(configurationData: Any,
                                             partialContext: BlockchainContext,
                                             blockSigMaker: SigMaker,
                                             eContext: EContext,
                                             cryptoSystem: CryptoSystem): Chain0BlockchainConfiguration {
        val configuration = factory.makeBlockchainConfiguration(configurationData, partialContext, blockSigMaker, eContext, cryptoSystem)
        return Chain0BlockchainConfiguration(configuration, appConfig)
    }
}