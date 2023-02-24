package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.network.mastersub.MasterSubQueryManager

open class Chain0BlockchainConfigurationFactory(val factory: GTXBlockchainConfigurationFactory, val appConfig: AppConfig) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockQueriesProvider: BlockQueriesProvider?,
            masterSubQueryManager: MasterSubQueryManager?
    ): Chain0BlockchainConfiguration {
        val configuration = factory.makeBlockchainConfiguration(configurationData, partialContext, blockSigMaker, eContext, cryptoSystem, blockQueriesProvider, masterSubQueryManager)
        return Chain0BlockchainConfiguration(
                configuration,
                configuration.module,
                appConfig
        )
    }
}