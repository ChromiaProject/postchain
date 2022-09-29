// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedEBFTInfrastructureFactory

// This is only used in tests, real D1 uses managed mode + containers
class D1TestInfrastructureFactory : ManagedEBFTInfrastructureFactory() {
    override fun makeProcessManager(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure, blockchainConfigurationProvider: BlockchainConfigurationProvider): BlockchainProcessManager {
        return ManagedBlockchainProcessManager(postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                listOf(AnchorTestProcessManagerExtension(postchainContext))
        )
    }
}
