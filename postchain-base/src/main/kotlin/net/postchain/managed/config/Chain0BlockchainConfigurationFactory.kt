package net.postchain.managed.config

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
        val managedDataSourceProvider: ManagedDataSourceProvider,
        val appConfig: AppConfig,
        val storage: Storage
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem
    ): Chain0BlockchainConfiguration {
        val config = factory.makeBlockchainConfiguration(configurationData, partialContext, blockSigMaker, eContext, cryptoSystem)
        val dataSource = managedDataSourceProvider.getDataSource(config, appConfig, storage)
        return Chain0BlockchainConfiguration(config, dataSource)
    }
}