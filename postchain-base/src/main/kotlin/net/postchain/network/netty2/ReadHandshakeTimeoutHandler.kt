package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.ReadTimeoutException
import java.util.concurrent.TimeUnit

class ReadHandshakeTimeoutHandler(
        connectionConfig: ConnectionConfig,
        private val isHandshaked: () -> Boolean
) : ChannelInboundHandlerAdapter() {

    private var closed = false
    private var channelActivatedTime: Long = 0
    val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(connectionConfig.readHandshakeTimeout)

    override fun channelActive(ctx: ChannelHandlerContext?) {
        channelActivatedTime = ticksInNanos()
        super.channelActive(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (closed) {
            (msg as ByteBuf).release()
            return
        }

        if (isHandshaked()) {
            super.channelRead(ctx, msg)
            return
        }

        if (ticksInNanos() - channelActivatedTime > timeoutNanos) {
            (msg as ByteBuf).release()
            ctx?.close()
            closed = true
            ctx?.fireExceptionCaught(ReadTimeoutException("Handshake timeout"))
            return
        }

        super.channelRead(ctx, msg)
    }

    private fun ticksInNanos(): Long {
        return System.nanoTime()
    }
}