package net.postchain.containers.bpm.chain0

import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.config.ManagedDataSourceProvider

class ContainerChain0BlockchainConfigurationFactory(
        val factory: GTXBlockchainConfigurationFactory,
        val managedDataSourceProvider: ManagedDataSourceProvider,
        val appConfig: AppConfig,
        val containerNodeConfig: ContainerNodeConfig,
        val storage: Storage
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem
    ): ContainerChain0BlockchainConfiguration {
        val config = factory.makeBlockchainConfiguration(configurationData, partialContext, blockSigMaker, eContext, cryptoSystem)
        val dataSource = managedDataSourceProvider.getDataSource(config, appConfig, storage)
        return ContainerChain0BlockchainConfiguration(config, dataSource)
    }
}
