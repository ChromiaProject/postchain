// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
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

class NettySubConnection(
        private val masterNode: PeerInfo,
        private val connectionDescriptor: SubConnectionDescriptor,
        eventLoopGroup: NioEventLoopGroup
) : ChannelInboundHandlerAdapter(), SubConnection {

    companion object : KLogging()

    private val nettyClient = NettyClient(eventLoopGroup)
    private lateinit var context: ChannelHandlerContext
    private var messageHandler: MsMessageHandler? = null
    private lateinit var onConnected: () -> Unit
    private lateinit var onDisconnected: () -> Unit
    private lateinit var channel: Channel

    fun open(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        this.onConnected = onConnected
        this.onDisconnected = onDisconnected

        nettyClient.apply {
            setChannelHandler(this@NettySubConnection)
            val future = connect(masterAddress()).await()
            if (future.isSuccess) {
                onConnected()
                channel = future.channel()
            } else {
                logger.info("Connection failed", future.cause())
                onDisconnected()
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
        onDisconnected()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val bytes = Transport.unwrapMessage(msg as ByteBuf)
        val message = MsCodec.decode(bytes)
        messageHandler?.onMessage(message)
        msg.release()
    }

    override fun accept(handler: MsMessageHandler) {
        messageHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        context.writeAndFlush(Transport.wrapMessage(packet()))
    }

    override fun remoteAddress(): String {
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        if (::channel.isInitialized) channel.close()
    }

    override fun descriptor(): SubConnectionDescriptor {
        return connectionDescriptor
    }

    private fun masterAddress(): SocketAddress {
        return InetSocketAddress(masterNode.host, masterNode.port)
    }

    private fun buildHandshakePacket(): ByteBuf {
        val message = MsHandshakeMessage(
                connectionDescriptor.blockchainRid.data, connectionDescriptor.peers)
        val bytes = MsCodec.encode(message)
        return Transport.wrapMessage(bytes)
    }
}