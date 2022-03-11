package net.postchain.network.common

import net.postchain.core.NodeRid

/**
 * Only used for debugging
 */
interface NetworkTopology {
    fun getNodesTopology(): Map<String, Map<String, String>>
    fun getNodesTopology(chainIid: Long): Map<NodeRid, String>
}