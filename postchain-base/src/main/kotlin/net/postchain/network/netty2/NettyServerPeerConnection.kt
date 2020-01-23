// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.postchain.network.XPacketDecoder
import net.postchain.network.x.LazyPacket
import net.postchain.network.x.XPacketHandler
import net.postchain.network.x.XPeerConnection
import net.postchain.network.x.XPeerConnectionDescriptor

class NettyServerPeerConnection<PacketType>(
        private val packetDecoder: XPacketDecoder<PacketType>
) : ChannelInboundHandlerAdapter(), XPeerConnection {

    private lateinit var context: ChannelHandlerContext
    private var packetHandler: XPacketHandler? = null
    private var peerConnectionDescriptor: XPeerConnectionDescriptor? = null

    private var onConnectedHandler: ((XPeerConnectionDescriptor, XPeerConnection) -> Unit)? = null
    private var onDisconnectedHandler: ((XPeerConnectionDescriptor, XPeerConnection) -> Unit)? = null

    override fun accept(handler: XPacketHandler) {
        this.packetHandler = handler
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
        context.close()
    }

    fun onConnected(handler: (XPeerConnectionDescriptor, XPeerConnection) -> Unit): NettyServerPeerConnection<PacketType> {
        this.onConnectedHandler = handler
        return this
    }

    fun onDisconnected(handler: (XPeerConnectionDescriptor, XPeerConnection) -> Unit): NettyServerPeerConnection<PacketType> {
        this.onDisconnectedHandler = handler
        return this
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val message = Transport.unwrapMessage(msg as ByteBuf)
        if (packetDecoder.isIdentPacket(message)) {
            val identPacketInfo = packetDecoder.parseIdentPacket(Transport.unwrapMessage(msg))
            peerConnectionDescriptor = XPeerConnectionDescriptor.createFromIdentPacketInfo(identPacketInfo)
            onConnectedHandler?.invoke(peerConnectionDescriptor!!, this)

        } else {
            if (peerConnectionDescriptor != null) {
                packetHandler?.invoke(message, peerConnectionDescriptor!!.peerId)
            }
        }
        (msg as ByteBuf).release()
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let { context = it }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        if (peerConnectionDescriptor != null) {
            onDisconnectedHandler?.invoke(peerConnectionDescriptor!!, this)
        }
    }
}
