package net.postchain.network.peer

import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.network.common.ChainWithConnections
import net.postchain.network.netty2.NettyPeerConnection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * This is the "Peer" implementation of [ChainWithConnections] , using the [NettyPeerConnection] implementation.
 *
 * On top of the basics it keeps track of what chain has what connections using [XChainPeersConfiguration]
 * and [NodeRid].
 */
class ChainWithPeerConnections(
    val iid: Long,
    val peerConfig: XChainPeersConfiguration,
    private val connectAll: Boolean
) : ChainWithConnections<PeerConnection, PeerPacketHandler> {

    val bcRid = peerConfig.blockchainRid // Just take it from the config

    private val connections = ConcurrentHashMap<NodeRid, PeerConnection>()

    override fun getChainIid(): Long = iid
    override fun getBlockchainRid(): BlockchainRid = bcRid

    override fun getPacketHandler(): PeerPacketHandler? {
        return peerConfig.peerPacketHandler
    }

    override fun shouldConnectAll(): Boolean = connectAll

    override fun isNodeBehavingWell(nodeId: NodeRid): Boolean {
        return peerConfig.commConfiguration.networkNodes.isNodeBehavingWell(nodeId, System.currentTimeMillis())
    }

    override fun getAllNodes() = connections.keys.toList()

    // ----------
    // Connections
    // ----------
    override fun isConnected(nodeId: NodeRid) = connections.containsKey(nodeId)
    override fun getConnection(nodeId: NodeRid) = connections[nodeId]
    override fun getAllConnections(): List<PeerConnection> {
        return connections.values.toList()
    }

    override fun setConnection(nodeId: NodeRid, conn: PeerConnection) {
        connections[nodeId] = conn
    }

    override fun closeConnections(): CompletableFuture<Void> {
        val closePromises = mutableListOf<CompletableFuture<Void>>()
        connections.forEach { (_, conn) -> closePromises.add(conn.close()) }
        connections.clear()
        return CompletableFuture.allOf(*closePromises.toTypedArray())
    }

    override fun removeAndCloseConnection(nodeId: NodeRid): CompletableFuture<Void>? {
        return connections.remove(nodeId)
                ?.close()
    }

    // ----------
    // Debug
    // ----------
    override fun getNodeTopology(): Map<NodeRid, String> {
        return connections.mapValues { connection ->
            (if (connection.value.descriptor().isOutgoing())
                "c-s"
            else
                "s-c") +
                    ", " + connection.value.remoteAddress()
        }
    }
}