package net.postchain.integrationtest.statemachine

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

class FailableTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext): BlockchainConfiguration {
        val owner = (configurationData as BlockchainConfigurationData)
                .context.nodeRID!!.toHex().uppercase()

        /*return TestBlockchainConfiguration(
                configData,
                createGtxModule(configData.context.blockchainRID, configData.data)
        )*/

        return if (owner == Nodes.pubKey0) {
            TestBlockchainConfiguration(
                    configurationData,
                    createGtxModule(configurationData.context.blockchainRID, configurationData, eContext)
            )
        } else {
            FailableTestBlockchainConfiguration(
                    configurationData,
                    createGtxModule(configurationData.context.blockchainRID, configurationData, eContext)
            )
        }

    }
}