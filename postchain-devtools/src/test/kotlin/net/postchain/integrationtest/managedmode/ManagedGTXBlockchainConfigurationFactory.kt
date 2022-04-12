// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.managedmode

import net.postchain.base.config.BlockchainConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModule

class ManagedGTXBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    companion object {
        val moduleRegistry = mutableMapOf<String, GTXModule>()
    }

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val baseConfigData = configurationData as BlockchainConfig
        val module = createGtxModule(baseConfigData.blockchainContext.blockchainRID, baseConfigData)
        val configuration = GTXBlockchainConfiguration(baseConfigData, module)
        moduleRegistry[module.javaClass.simpleName] = module
        return configuration
    }
}