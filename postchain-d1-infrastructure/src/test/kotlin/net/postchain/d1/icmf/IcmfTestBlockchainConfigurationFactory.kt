package net.postchain.d1.icmf

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class IcmfTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        return IcmfTestBlockchainConfiguration(
                configurationData as BlockchainConfigurationData,
                createGtxModule(configurationData.context.blockchainRID, configurationData)
        )
    }
}
