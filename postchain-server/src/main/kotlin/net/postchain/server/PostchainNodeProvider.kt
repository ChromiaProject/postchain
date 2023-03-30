package net.postchain.server

import net.postchain.PostchainNode

class PostchainNodeProvider(private val postchainNode: PostchainNode) : NodeProvider {
    override fun get(): PostchainNode = postchainNode
}
