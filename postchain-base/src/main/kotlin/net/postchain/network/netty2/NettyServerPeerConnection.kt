// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import mu.KLogging
import net.postchain.core.ProgrammerMistake
import net.postchain.network.XPacketDecoder
import net.postchain.network.common.*
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerConnectionDescriptorFactory
import net.postchain.network.peer.PeerPacketHandler

class NettyServerPeerConnection<PacketType>(
        private val packetDecoder: XPacketDecoder<PacketType>
) : NettyPeerConnection() {

    private var context: ChannelHandlerContext? = null
    private var peerPacketHandler: PeerPacketHandler? = null
    private var peerConnectionDescriptor: PeerConnectionDescriptor? = null

    private var onConnectedHandler: ((NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>) -> Unit)? = null
    private var onDisconnectedHandler: ((NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>) -> Unit)? = null

    companion object: KLogging()

    override fun accept(packetHandler: PeerPacketHandler) {
        this.peerPacketHandler = packetHandler
    }

    override fun sendPacket(packet: LazyPacket): Boolean {
        return if (context == null) {
            false
        } else {
            context!!.writeAndFlush(Transport.wrapMessage(packet()))
            true
        }
    }

    override fun remoteAddress(): String {
        return if (context != null)
            context!!.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        context?.close()
    }

    override fun descriptor(): PeerConnectionDescriptor {
        return peerConnectionDescriptor ?: throw ProgrammerMistake("Descriptor is null")
    }

    fun onConnected(handler: (NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>) -> Unit): NettyServerPeerConnection<PacketType> {
        this.onConnectedHandler = handler
        return this
    }

    fun onDisconnected(handler: (NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>) -> Unit): NettyServerPeerConnection<PacketType> {
        this.onDisconnectedHandler = handler
        return this
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        handleSafely(peerConnectionDescriptor?.nodeId) {
            val message = Transport.unwrapMessage(msg as ByteBuf)
            if (packetDecoder.isIdentPacket(message)) {
                val identPacketInfo = packetDecoder.parseIdentPacket(Transport.unwrapMessage(msg))
                peerConnectionDescriptor = PeerConnectionDescriptorFactory.createFromIdentPacketInfo(identPacketInfo)
                onConnectedHandler?.invoke(this)
            } else {
                if (peerConnectionDescriptor != null) {
                    peerPacketHandler?.handle(message, peerConnectionDescriptor!!.nodeId!!)
                }
            }
            (msg as ByteBuf).release()
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let { context = it }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        // If peerConnectionDescriptor is null, we can't do much handling
        // in which case we just ignore the inactivation of this channel.
        if (peerConnectionDescriptor != null) {
            onDisconnectedHandler?.invoke(this)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        logger.debug("Error on connection.", cause)
    }
}
