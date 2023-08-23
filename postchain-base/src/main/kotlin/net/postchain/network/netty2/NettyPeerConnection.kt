package net.postchain.network.netty2

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.network.peer.PeerConnection

abstract class NettyPeerConnection :
    ChannelInboundHandlerAdapter(),
    PeerConnection {

    companion object : KLogging() {
        const val PING_SEND_INTERVAL = 30
        const val READ_TIMEOUT = 60
    }

    private val pingBytes = ByteArray(1) { 1 }

    fun handleSafely(nodeId: NodeRid?, handler: () -> Unit) {
        try {
            handler()
        } catch (e: Exception) {
            logger.error("Error when receiving message from peer $nodeId", e)
        }
    }

    protected fun isPing(msg: ByteArray) = pingBytes.contentEquals(msg)

    protected fun sendPing(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(Transport.wrapMessage(pingBytes))
    }

    protected fun registerIdleStateHandler(ctx: ChannelHandlerContext) {
        ctx.channel().pipeline().addFirst(
                IdleStateHandler(READ_TIMEOUT, PING_SEND_INTERVAL, 0)
        )
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt is IdleStateEvent) {
            if (evt.state() == IdleState.READER_IDLE) {
                logger.info("No messages received within read timeout, closing connection")
                ctx?.close()
            } else if (evt.state() == IdleState.WRITER_IDLE) {
                ctx?.writeAndFlush(Transport.wrapMessage(pingBytes))
            }
        }
    }
}
