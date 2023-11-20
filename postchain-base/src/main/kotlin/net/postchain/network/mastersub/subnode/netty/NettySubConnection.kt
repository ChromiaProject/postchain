// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.EventLoopGroup
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.common.LazyPacket
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsHandshakeMessage
import net.postchain.network.mastersub.subnode.SubConnection
import net.postchain.network.mastersub.subnode.SubConnectionDescriptor
import net.postchain.network.netty2.NettyClient
import net.postchain.network.netty2.Transport
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.CompletableFuture

class NettySubConnection(
        private val masterNode: PeerInfo,
        private val connectionDescriptor: SubConnectionDescriptor,
        private val eventLoopGroup: EventLoopGroup
) : ChannelInboundHandlerAdapter(), SubConnection {

    companion object : KLogging()

    private var nettyClient: NettyClient? = null
    private lateinit var context: ChannelHandlerContext
    private var messageHandler: MsMessageHandler? = null
    private lateinit var onConnected: () -> Unit

    private val channelInactiveFuture = CompletableFuture<Void>()

    fun open(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        this.onConnected = onConnected
        channelInactiveFuture.thenApply { onDisconnected() }

        nettyClient = NettyClient(this@NettySubConnection, masterAddress(), eventLoopGroup).also {
            it.channelFuture.await().apply {
                if (!isSuccess) {
                    logger.info("Connection failed: ${cause().message}")
                    channelInactiveFuture.complete(null)
                }
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let {
            context = ctx
            context.writeAndFlush(buildHandshakePacket())
            onConnected()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        channelInactiveFuture.complete(null)
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val bytes = Transport.unwrapMessage(msg as ByteBuf)
        try {
            val message = MsCodec.decode(bytes)
            messageHandler?.onMessage(message)
        } finally {
            msg.release()
        }
    }

    override fun accept(handler: MsMessageHandler) {
        messageHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        context.writeAndFlush(Transport.wrapMessage(packet.value))
    }

    override fun remoteAddress(): String {
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close(): CompletableFuture<Void> {
        nettyClient?.shutdownAsync()
        return channelInactiveFuture
    }

    override fun descriptor(): SubConnectionDescriptor {
        return connectionDescriptor
    }

    private fun masterAddress(): SocketAddress {
        return InetSocketAddress(masterNode.host, masterNode.port)
    }

    private fun buildHandshakePacket(): ByteBuf {
        val message = MsHandshakeMessage(
                connectionDescriptor.blockchainRid?.data, connectionDescriptor.peers, connectionDescriptor.containerIID)
        val bytes = MsCodec.encode(message)
        return Transport.wrapMessage(bytes)
    }
}