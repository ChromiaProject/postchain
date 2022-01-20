// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import net.postchain.core.NodeRid
import net.postchain.network.common.ConnectionManager

/**
 * This interface extends the [ConnectionManager] with various operations where you can manage the individual
 * peer connections yourself (usually not needed, since it is handled automatically).
 *
 */
interface PeerConnectionManager : ConnectionManager {

    /**
     * Connect a specific node to the given chain
     */
    fun connectChainPeer(chainId: Long, peerId: NodeRid)

    /**
     * Disconnect a specific node from the given chain
     */
    fun disconnectChainPeer(chainId: Long, peerId: NodeRid)

    /**
     * @return "true" if the given node is connected to the given chain.
     */
    fun isPeerConnected(chainId: Long, peerId: NodeRid): Boolean

    /**
     * @return all nodes connected to the given chain
     */
    fun getConnectedPeers(chainId: Long): List<NodeRid>

}
