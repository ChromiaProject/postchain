package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration

class AnchorTestProcessManagerExtension(postchainContext: PostchainContext) : AnchorProcessManagerExtension(postchainContext) {
   override fun createClusterManagement(configuration: BlockchainConfiguration) = AnchorTestClusterManagement()
}
