package net.postchain.managed

import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory


class AnyBlockchainConfigFactory : BlockchainConfigurationFactory {
    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ): BlockchainConfiguration {
        TODO("Not yet implemented")
    }
}

class ExtendedBcConfigFactory : GTXBlockchainConfigurationFactory()
