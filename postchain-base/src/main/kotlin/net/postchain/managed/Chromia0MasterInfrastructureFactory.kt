package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.core.BlockchainProcessManagerExtension

/**
 * TODO: this is currently used, via configuration. It will be replaced by the new Anchoring process.
 */
class Chromia0MasterInfrastructureFactory : MasterManagedEbftInfraFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext): List<BlockchainProcessManagerExtension> {
        return listOf(LegacyAnchoringBlockchainProcessManagerExtension(postchainContext))
    }
}
