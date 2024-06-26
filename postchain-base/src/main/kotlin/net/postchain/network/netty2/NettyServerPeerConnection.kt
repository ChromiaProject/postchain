// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.network.XPacketCodec
import net.postchain.network.common.LazyPacket
import net.postchain.network.peer.PeerConnection
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerConnectionDescriptorFactory
import net.postchain.network.peer.PeerPacketHandler
import java.util.concurrent.CompletableFuture

class NettyServerPeerConnection<PacketType>(
        packetCodec: XPacketCodec<PacketType>
) : NettyPeerConnection<PacketType>(packetCodec) {

    private lateinit var context: ChannelHandlerContext
    private var peerPacketHandler: PeerPacketHandler? = null
    private var peerConnectionDescriptor: PeerConnectionDescriptor? = null

    private var onConnectedHandler: ((PeerConnection) -> Unit)? = null
    private var onDisconnectedHandler: ((PeerConnection) -> Unit)? = null

    private var hasReceivedPing = false
    private var hasReceivedVersion = false

    private val channelInactiveFuture = CompletableFuture<Void>()

    companion object : KLogging()

    override fun accept(handler: PeerPacketHandler) {
        this.peerPacketHandler = handler
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
        context.close()
        return channelInactiveFuture
    }

    override fun descriptor(): PeerConnectionDescriptor {
        return peerConnectionDescriptor ?: throw ProgrammerMistake("Descriptor is null")
    }

    fun onConnected(handler: (PeerConnection) -> Unit): NettyServerPeerConnection<PacketType> {
        this.onConnectedHandler = handler
        return this
    }

    fun onDisconnected(handler: (PeerConnection) -> Unit): NettyServerPeerConnection<PacketType> {
        this.onDisconnectedHandler = handler
        return this
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val message = Transport.unwrapMessage(msg as ByteBuf)
        try {
            if (isPing(message)) {
                handlePing(ctx)
            } else if (isVersion(message)) {
                handleVersion(message, ctx)
            } else {
                handleMessage(message, ctx)
            }
        } catch (e: Exception) {
            logger.error("Error when receiving message from peer ${peerConnectionDescriptor?.nodeId}", e)
        } finally {
            msg.release()
        }
    }

    private fun handleVersion(message: ByteArray, ctx: ChannelHandlerContext?) {
        if (!hasReceivedVersion) {
            logger.debug { "Got packet version from ${descriptor().nodeId.toHex()}" }
            hasReceivedVersion = true
            handleMessage(message, ctx)
        }
    }

    private fun handlePing(ctx: ChannelHandlerContext?) {
        if (!hasReceivedPing) {
            ctx?.let { registerIdleStateHandler(it) }
            hasReceivedPing = true
        }
    }

    private fun handleMessage(message: ByteArray, ctx: ChannelHandlerContext?) {
        if (packetCodec.isIdentPacket(message)) {
            val identPacketInfo = packetCodec.parseIdentPacket(message)
            peerConnectionDescriptor = PeerConnectionDescriptorFactory.createFromIdentPacketInfo(identPacketInfo)

            // Notify peer that we have ping capability
            ctx?.let {
                sendVersion(it)
                sendPing(it)
            }
            onConnectedHandler?.invoke(this)
        } else {
            if (peerConnectionDescriptor != null) {
                peerPacketHandler?.handle(message, peerConnectionDescriptor!!.nodeId)
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let { context = it }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        channelInactiveFuture.complete(null)
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
