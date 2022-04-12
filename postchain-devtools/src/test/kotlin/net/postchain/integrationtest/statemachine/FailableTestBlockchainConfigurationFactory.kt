package net.postchain.integrationtest.statemachine

import net.postchain.base.config.BlockchainConfig
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfiguration
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.devtools.testinfra.TestBlockchainConfigurationFactory

class FailableTestBlockchainConfigurationFactory : TestBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configData: Any): BlockchainConfiguration {
        val owner = (configData as BlockchainConfig)
                .blockchainContext.nodeRID!!.toHex().toUpperCase()

        /*return TestBlockchainConfiguration(
                configData,
                createGtxModule(configData.context.blockchainRID, configData.data)
        )*/

        return if (owner == Nodes.pubKey0) {
            TestBlockchainConfiguration(
                    configData,
                    createGtxModule(configData.blockchainContext.blockchainRID, configData)
            )
        } else {
            FailableTestBlockchainConfiguration(
                    configData,
                    createGtxModule(configData.blockchainContext.blockchainRID, configData)
            )
        }

    }
}