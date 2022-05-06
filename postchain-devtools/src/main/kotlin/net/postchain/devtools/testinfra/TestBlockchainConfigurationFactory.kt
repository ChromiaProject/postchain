// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import net.postchain.core.BlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class TestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configData: Any): BlockchainConfiguration {
        return TestBlockchainConfiguration(
                configData as BlockchainConfigurationData,
                createGtxModule(configData.context.blockchainRID, configData)
        )
    }
}