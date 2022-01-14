package net.postchain.network.netty2

import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KLogging
import net.postchain.network.common.NodeConnection
import net.postchain.core.NodeRid
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler

abstract class NettyPeerConnection:
    ChannelInboundHandlerAdapter(),
    NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>
{
    companion object: KLogging()

    fun handleSafely(nodeId: NodeRid?, handler: () -> Unit) {
        try {
            handler()
        } catch (e: Exception) {
            logger.error("Error when receiving message from peer ${nodeId}", e)
        }
    }
}