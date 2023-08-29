// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class TestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ) = TestBlockchainConfiguration(
            configurationData as BlockchainConfigurationData,
            cryptoSystem,
            partialContext,
            blockSigMaker,
            createGtxModule(partialContext.blockchainRID, configurationData, eContext),
            blockchainConfigurationOptions
    )
}
