package net.postchain.d1.icmf

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.EContext
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXModule

class IcmfTestBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        module: GTXModule
) : TestBlockchainConfiguration(configData, module) {
    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        module.initializeDB(ctx)
    }
}
