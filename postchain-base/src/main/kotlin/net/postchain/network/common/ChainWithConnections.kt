package net.postchain.network.common

import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import java.util.concurrent.CompletableFuture

/**
 * This is a wrapped collection of connections.
 * It knows what connections ([NodeConnectionType]) a certain Blockchain has at this moment in time.
 * We also packed some utility things, like chain info and the packet handler for this chain.
 *
 * @property NodeConnectionType is the type of connection we are handling
 * @property HandlerType
 */
interface ChainWithConnections<NodeConnectionType, HandlerType> {

    /**
     * @return the chain IID
     */
    fun getChainIid(): Long

    /**
     * @return the BC RID or throw
     */
    fun getBlockchainRid(): BlockchainRid

    /**
     * @return false if the node is too troublesome
     */
    fun isNodeBehavingWell(nodeId: NodeRid): Boolean

    fun getPacketHandler(): HandlerType?

    fun shouldConnectAll(): Boolean

    fun getAllNodes(): List<NodeRid>

    // ----------
    // Connections
    // This type has an internal collection of connections
    // ----------
    fun isConnected(nodeId: NodeRid): Boolean
    fun getConnection(nodeId: NodeRid): NodeConnectionType?
    fun getAllConnections(): List<NodeConnectionType>
    fun setConnection(nodeId: NodeRid, conn: NodeConnectionType)

    /**
     * Will close any connection related to this chain
     */
    fun closeConnections(): CompletableFuture<Void>

    fun removeAndCloseConnection(nodeId: NodeRid): CompletableFuture<Void>?

    // ----------
    // Topology
    // ----------
    fun getNodeTopology(): Map<NodeRid, String>
}