// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.network.mastersub.MasterSubQueryManager

open class TestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockQueriesProvider: BlockQueriesProvider?,
            masterSubQueryManager: MasterSubQueryManager?
    ): TestBlockchainConfiguration {
        return TestBlockchainConfiguration(
                configurationData as BlockchainConfigurationData,
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
