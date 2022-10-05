package net.postchain.managed

import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtx.GTXBlockchainConfigurationFactory


class AnyBlockchainConfigFactory : BlockchainConfigurationFactory {
    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext, cryptoSystem: CryptoSystem): BlockchainConfiguration {
        TODO("Not yet implemented")
    }
}
class ExtendedBcConfigFactory() : GTXBlockchainConfigurationFactory()

