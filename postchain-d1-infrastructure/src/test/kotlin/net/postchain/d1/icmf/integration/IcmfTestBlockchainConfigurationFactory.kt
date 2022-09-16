package net.postchain.d1.icmf.integration

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.ModuleInitializer

open class IcmfTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any, moduleInitializer: ModuleInitializer): BlockchainConfiguration {
        val baseCfgData = configurationData as BlockchainConfigurationData
        val module = createGtxModule(baseCfgData.context.blockchainRID, configurationData)
        moduleInitializer(module)
        return IcmfTestBlockchainConfiguration(configurationData, module
        )
    }
}
