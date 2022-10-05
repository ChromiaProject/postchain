// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class TestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext, cryptoSystem: CryptoSystem): TestBlockchainConfiguration {
        return TestBlockchainConfiguration(
                configurationData as BlockchainConfigurationData,
                cryptoSystem,
                createGtxModule(configurationData.context.blockchainRID, configurationData, eContext)
        )
    }
}