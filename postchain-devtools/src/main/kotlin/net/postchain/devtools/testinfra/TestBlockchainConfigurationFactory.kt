// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.ModuleInitializer

open class TestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configData: Any, moduleInitializer: ModuleInitializer): BlockchainConfiguration {
        return TestBlockchainConfiguration(
                configData as BlockchainConfigurationData,
                createGtxModule(configData.context.blockchainRID, configData)
        )
    }
}