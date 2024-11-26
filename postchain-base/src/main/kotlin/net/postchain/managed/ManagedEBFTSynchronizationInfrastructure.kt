package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure

open class ManagedEBFTSynchronizationInfrastructure(postchainContext: PostchainContext
) : EBFTSynchronizationInfrastructure(postchainContext, DefaultManagedPeersCommConfigFactory())
