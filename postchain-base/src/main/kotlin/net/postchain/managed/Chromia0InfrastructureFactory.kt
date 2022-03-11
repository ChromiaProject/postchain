// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager

class Chromia0InfrastructureFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {
        return Chromia0BlockchainProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigurationProvider)
    }
}