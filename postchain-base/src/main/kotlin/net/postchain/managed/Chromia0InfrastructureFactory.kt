// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManagerExtension

/**
 * TODO: Olle: this is currently used, via configuration. It will be replaced by the new Anchoring process.
 */
class Chromia0InfrastructureFactory: ManagedEBFTInfrastructureFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure): List<BlockchainProcessManagerExtension> {
        return listOf(LegacyAnchoringBlockchainProcessManagerExtension(postchainContext))
    }
}