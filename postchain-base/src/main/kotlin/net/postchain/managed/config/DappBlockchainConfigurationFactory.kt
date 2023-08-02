package net.postchain.managed.config

import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.ManagedNodeDataSource

open class DappBlockchainConfigurationFactory(
        val factory: GTXBlockchainConfigurationFactory,
        val dataSource: ManagedNodeDataSource
) : BlockchainConfigurationFactory by factory {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem
    ): DappBlockchainConfiguration {
        val config = factory.makeBlockchainConfiguration(configurationData, partialContext, blockSigMaker, eContext, cryptoSystem)
        return DappBlockchainConfiguration(config, dataSource)
    }
}