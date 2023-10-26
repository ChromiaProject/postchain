// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.EventLoopGroup
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.network.XPacketEncoder
import net.postchain.network.common.LazyPacket
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import java.net.InetSocketAddress
import java.net.SocketAddress

class NettyClientPeerConnection<PacketType>(
        private val peerInfo: PeerInfo,
        private val packetEncoder: XPacketEncoder<PacketType>,
        private val descriptor: PeerConnectionDescriptor,
        private val eventLoopGroup: EventLoopGroup
) : NettyPeerConnection() {

    companion object : KLogging()

    private var nettyClient: NettyClient? = null
    private var hasReceivedPing = false
    private var peerPacketHandler: PeerPacketHandler? = null
    private lateinit var context: ChannelHandlerContext
    private lateinit var onConnected: () -> Unit
    private lateinit var onDisconnected: () -> Unit

    fun open(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        this.onConnected = onConnected
        this.onDisconnected = onDisconnected

        nettyClient = NettyClient(this@NettyClientPeerConnection, peerAddress(), eventLoopGroup).also {
            it.channelFuture.addListener { future ->
                if (!future.isSuccess) {
                    logger.info("Connection failed: ${future.cause().message}")
                    onDisconnected()
                }
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let {
            context = it
            context.writeAndFlush(buildIdentPacket())
            onConnected()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        onDisconnected()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        handleSafely(peerInfo.peerId()) {
            val message = Transport.unwrapMessage(msg as ByteBuf)
            if (!isPing(message)) {
                peerPacketHandler?.handle(
                        message,
                        peerInfo.peerId())
            } else if (!hasReceivedPing) {
                // Peer has ping capability
                ctx?.let {
                    // Notify peer that we also have ping capability
                    sendPing(it)
                    registerIdleStateHandler(it)
                }
                hasReceivedPing = true
            }
            msg.release()
        }
    }

    override fun accept(handler: PeerPacketHandler) {
        peerPacketHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        context.writeAndFlush(Transport.wrapMessage(packet.value))
    }

    override fun remoteAddress(): String {
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        nettyClient?.shutdownAsync()
    }

    override fun descriptor(): PeerConnectionDescriptor = descriptor

    private fun peerAddress(): SocketAddress {
        return InetSocketAddress(peerInfo.host, peerInfo.port)
    }

    private fun buildIdentPacket(): ByteBuf {
        return Transport.wrapMessage(
                packetEncoder.makeIdentPacket(peerInfo.getNodeRid()))
    }
}