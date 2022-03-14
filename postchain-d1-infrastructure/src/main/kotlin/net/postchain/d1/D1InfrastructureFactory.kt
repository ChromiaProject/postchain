// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.d1.anchor.IcmfProcessManagerExtension
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedEBFTInfrastructureFactory

class D1InfrastructureFactory: ManagedEBFTInfrastructureFactory() {
    override fun makeProcessManager(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure, blockchainConfigurationProvider: BlockchainConfigurationProvider): BlockchainProcessManager {
        return ManagedBlockchainProcessManager(postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                listOf(IcmfProcessManagerExtension(postchainContext))
        )
    }
}