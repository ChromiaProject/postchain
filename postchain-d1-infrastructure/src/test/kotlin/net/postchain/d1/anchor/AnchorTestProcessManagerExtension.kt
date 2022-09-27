package net.postchain.d1.anchor

import net.postchain.PostchainContext

class AnchorTestProcessManagerExtension(postchainContext: PostchainContext) : AnchorProcessManagerExtension(postchainContext) {

    override fun createClusterManagement() = AnchorTestClusterManagement()
}