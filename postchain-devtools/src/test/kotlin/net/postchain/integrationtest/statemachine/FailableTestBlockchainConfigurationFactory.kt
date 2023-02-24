package net.postchain.integrationtest.statemachine

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.toHex
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.network.mastersub.MasterSubQueryManager

class FailableTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockQueriesProvider: BlockQueriesProvider?,
            masterSubQueryManager: MasterSubQueryManager?
    ): TestBlockchainConfiguration {
        val owner = partialContext.nodeRID!!.toHex().uppercase()

        /*return TestBlockchainConfiguration(
                configData,
                createGtxModule(configData.context.blockchainRID, configData.data)
        )*/

        configurationData as BlockchainConfigurationData

        return if (owner == Nodes.pubKey0) {
            TestBlockchainConfiguration(
                    configurationData,
                    cryptoSystem,
                    partialContext,
                    blockSigMaker,
                    createGtxModule(
                            partialContext.blockchainRID,
                            configurationData,
                            eContext,
                            blockQueriesProvider,
                            masterSubQueryManager
                    )
            )
        } else {
            FailableTestBlockchainConfiguration(
                    configurationData,
                    cryptoSystem,
                    partialContext,
                    blockSigMaker,
                    createGtxModule(
                            partialContext.blockchainRID,
                            configurationData,
                            eContext,
                            blockQueriesProvider,
                            masterSubQueryManager
                    )
            )
        }

    }
}