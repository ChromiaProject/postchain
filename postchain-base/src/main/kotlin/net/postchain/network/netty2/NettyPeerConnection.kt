package net.postchain.network.netty2

import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.network.peer.PeerConnection

abstract class NettyPeerConnection :
    ChannelInboundHandlerAdapter(),
    PeerConnection {

    companion object : KLogging()

    fun handleSafely(nodeId: NodeRid?, handler: () -> Unit) {
        try {
            handler()
        } catch (e: Exception) {
            logger.error("Error when receiving message from peer $nodeId", e)
        }
    }
}