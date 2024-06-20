package net.postchain.network.peer

import net.postchain.base.NetworkNodes
import net.postchain.base.PeerInfo

object NetworkNodesHelper {

    fun buildDummyNetworkNodes(): NetworkNodes {
        return NetworkNodes(PeerInfo("abc", 1, byteArrayOf(1)), mapOf(), mutableMapOf())
    }
}
