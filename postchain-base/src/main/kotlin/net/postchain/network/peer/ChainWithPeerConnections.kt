package net.postchain.network.peer

import net.postchain.core.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.network.common.ChainWithConnections
import net.postchain.network.common.NodeConnection
import net.postchain.network.netty2.NettyPeerConnection

/**
 * This is the "Peer" implementation of [ChainWithConnections] , using the [NettyPeerConnection] implementation.
 *
 * On top of the basics it keeps track of what chain has what connections using [XChainPeersConfiguration]
 * and [NodeRid].
 */
class ChainWithPeerConnections (
    val iid: Long,
    val peerConfig: XChainPeersConfiguration,
    private val connectAll: Boolean
) : ChainWithConnections<NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>, PeerPacketHandler> {

    val bcRid = peerConfig.blockchainRid // Just take it from the config

    private val connections = mutableMapOf<
            NodeRid,
            NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>>()

    override fun getChainIid(): Long = iid
    override fun getBlockchainRid(): BlockchainRid = bcRid

    override fun getPacketHandler(): PeerPacketHandler? {
        return peerConfig.peerPacketHandler
    }

    override fun shouldConnectAll(): Boolean = connectAll

    override fun isNodeBehavingWell(peerId: NodeRid): Boolean {
        return peerConfig.commConfiguration.networkNodes.isNodeBehavingWell(peerId, System.currentTimeMillis())
    }

    override fun getAllNodes() = connections.keys.toList()

    // ----------
    // Connections
    // ----------
    override fun isConnected(nodeId: NodeRid) = connections.containsKey(nodeId)
    override fun getConnection(peerId: NodeRid) = connections[peerId]
    override fun getAllConnections(): List<NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>> {
        return connections.values.toList()
    }

    override fun setConnection(peerId: NodeRid, conn: NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>) {
        connections[peerId] = conn
    }

    override fun closeConnections() {
        connections.forEach { (_, conn) -> conn.close() }
        connections.clear()
    }

    override fun removeAndCloseConnection(peerId: NodeRid) {
        connections.remove(peerId)
            ?.close()
    }

    // ----------
    // Debug
    // ----------
    override fun getNodeTopology(): Map<NodeRid, String> {
        return connections?.mapValues { connection ->
            (if (connection.value.descriptor()
                    .isOutgoing()
            ) "c-s" else "s-c") + ", " + connection.value.remoteAddress()
        }
    }
}