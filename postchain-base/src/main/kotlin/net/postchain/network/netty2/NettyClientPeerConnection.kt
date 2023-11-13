// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.EventLoopGroup
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.network.XPacketCodec
import net.postchain.network.common.LazyPacket
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import java.net.InetSocketAddress
import java.net.SocketAddress

class NettyClientPeerConnection<PacketType>(
        private val peerInfo: PeerInfo,
        packetCodec: XPacketCodec<PacketType>,
        private val descriptor: PeerConnectionDescriptor,
        private val eventLoopGroup: EventLoopGroup
) : NettyPeerConnection<PacketType>(packetCodec) {

    companion object : KLogging()

    private var nettyClient: NettyClient? = null
    private var hasReceivedPing = false
    private var hasReceivedVersion = false
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
            sendVersion(it)
            onConnected()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        onDisconnected()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val message = Transport.unwrapMessage(msg as ByteBuf)
        try {
            if (isPing(message)) {
                handlePing(ctx)
            } else if (isVersion(message)) {
                handleVersion(message)
            } else {
                peerPacketHandler?.handle(message, peerInfo.peerId())
            }
        } catch (e: Exception) {
            logger.error("Error when receiving message from peer ${peerInfo.peerId()}", e)
        } finally {
            msg.release()
        }
    }

    private fun handleVersion(message: ByteArray) {
        if (!hasReceivedVersion) {
            logger.debug { "Got packet version from ${descriptor.nodeId.toHex()}" }
            hasReceivedVersion = true
            peerPacketHandler?.handle(message, peerInfo.peerId())
        }
    }

    private fun handlePing(ctx: ChannelHandlerContext?) {
        // Peer has ping capability
        if (!hasReceivedPing) {
            ctx?.let {
                // Notify peer that we also have ping capability
                sendPing(it)
                registerIdleStateHandler(it)
            }
            hasReceivedPing = true
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

    private fun buildIdentPacket(): ByteBuf = Transport.wrapMessage(packetCodec.makeIdentPacket(peerInfo.getNodeRid()))
}