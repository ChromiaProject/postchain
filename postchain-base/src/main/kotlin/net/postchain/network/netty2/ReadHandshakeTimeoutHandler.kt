package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.ReadTimeoutException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ReadHandshakeTimeoutHandler(
        connectionConfig: ConnectionConfig,
        private val isHandshaked: () -> Boolean
) : ChannelInboundHandlerAdapter() {

    private var closed = false
    private var channelActivatedTime: Long = 0
    private var timeoutFuture: ScheduledFuture<*>? = null
    private val handshakeTimeoutMillis = connectionConfig.readHandshakeTimeout
    val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(connectionConfig.readHandshakeTimeout)

    override fun channelActive(ctx: ChannelHandlerContext?) {
        val context = ctx ?: return super.channelActive(ctx)
        channelActivatedTime = ticksInNanos()
        if (timeoutFuture == null && !isHandshaked()) {
            timeoutFuture = context.executor().schedule(
                    { closeOnTimeout(context) },
                    handshakeTimeoutMillis,
                    TimeUnit.MILLISECONDS
            )
        }
        super.channelActive(ctx)
    }

    override fun handlerAdded(ctx: ChannelHandlerContext?) {
        val context = ctx ?: return super.handlerAdded(ctx)
        if (context.channel().isActive && timeoutFuture == null && !isHandshaked()) {
            channelActivatedTime = ticksInNanos()
            timeoutFuture = context.executor().schedule(
                    { closeOnTimeout(context) },
                    handshakeTimeoutMillis,
                    TimeUnit.MILLISECONDS
            )
        }
        super.handlerAdded(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (closed) {
            (msg as ByteBuf).release()
            return
        }

        if (isHandshaked()) {
            cancelTimeout()
            super.channelRead(ctx, msg)
            return
        }

        if (ticksInNanos() - channelActivatedTime > timeoutNanos) {
            (msg as ByteBuf).release()
            ctx?.let { closeOnTimeout(it) }
            return
        }

        super.channelRead(ctx, msg)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        cancelTimeout()
        super.channelInactive(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        cancelTimeout()
        super.handlerRemoved(ctx)
    }

    private fun closeOnTimeout(ctx: ChannelHandlerContext) {
        if (closed || isHandshaked()) return
        closed = true
        cancelTimeout()
        ctx.close()
        ctx.fireExceptionCaught(ReadTimeoutException("Handshake timeout"))
    }

    private fun cancelTimeout() {
        timeoutFuture?.cancel(false)
        timeoutFuture = null
    }

    private fun ticksInNanos(): Long {
        return System.nanoTime()
    }
}
