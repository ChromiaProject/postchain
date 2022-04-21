package net.postchain.integrationtest.statemachine

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfiguration
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class FailableTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val owner = (configurationData as BlockchainConfigurationData)
                .context.nodeRID!!.toHex().toUpperCase()

        /*return TestBlockchainConfiguration(
                configData,
                createGtxModule(configData.context.blockchainRID, configData.data)
        )*/

        return if (owner == Nodes.pubKey0) {
            TestBlockchainConfiguration(
                    configurationData,
                    createGtxModule(configurationData.context.blockchainRID, configurationData)
            )
        } else {
            FailableTestBlockchainConfiguration(
                    configurationData,
                    createGtxModule(configurationData.context.blockchainRID, configurationData)
            )
        }

    }
}