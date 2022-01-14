package net.postchain.network.common

import net.postchain.network.peer.XChainPeersConfiguration
import net.postchain.core.NodeRid
import net.postchain.core.Shutdownable

/**
 * Holds the functions signatures common to all "connection managers".
 */
interface ConnectionManager: NetworkTopology, Shutdownable {

    /**
     * Connect the given chain to the network (for example "connect to all peers in the consensus network")
     */
    fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String)

    /**
     * Disconnect all nodes from the chain
     */
    fun disconnectChain(loggingPrefix: () -> String, chainId: Long)

    /**
     * Send a packet to the given chain at the given node
     */
    fun sendPacket(data: LazyPacket, chainId: Long, nodeRid: NodeRid)

    /**
     * Send the packet to ever connection the we have for the given chain
     */
    fun broadcastPacket(data: LazyPacket, chainId: Long)

}