// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager

/**
 * TODO: Olle: this is currently used, via configuration. It will be replaced by the new Anchoring process.
 */
class Chromia0InfrastructureFactory: ManagedEBFTInfrastructureFactory() {

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {
        return ManagedBlockchainProcessManager(
                postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                listOf(OldAnchoringBlockchainProcessManagerExtension(postchainContext))
        )
    }
}