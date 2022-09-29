package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.managed.ManagedBlockchainConfiguration

class AnchorTestProcessManagerExtension(postchainContext: PostchainContext) : AnchorProcessManagerExtension(postchainContext) {
   override fun createClusterManagement(configuration: ManagedBlockchainConfiguration) = AnchorTestClusterManagement()
}
