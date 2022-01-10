// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.network.XPacketEncoder
import net.postchain.network.common.LazyPacket
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import nl.komponents.kovenant.task
import java.net.InetSocketAddress
import java.net.SocketAddress

class NettyClientPeerConnection<PacketType>(
        private val peerInfo: PeerInfo,
        private val packetEncoder: XPacketEncoder<PacketType>,
        private val descriptor: PeerConnectionDescriptor
) : NettyPeerConnection() {

    companion object : KLogging()

    private val nettyClient = NettyClient()
    private lateinit var context: ChannelHandlerContext
    private var peerPacketHandler: PeerPacketHandler? = null
    private lateinit var onDisconnected: () -> Unit

    fun open(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        this.onDisconnected = onDisconnected

        nettyClient.apply {
            setChannelHandler(this@NettyClientPeerConnection)
            val future = connect(peerAddress()).await()
            if (future.isSuccess) {
                onConnected()
            } else {
                logger.info("Connection failed", future.cause().message)
                onDisconnected()
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        //logger.debug("Activate channel ---")
        ctx?.let {
            context = ctx
            context.writeAndFlush(buildIdentPacket())
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        onDisconnected()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        handleSafely(peerInfo.peerId()) {
            peerPacketHandler?.handle(
                    Transport.unwrapMessage(msg as ByteBuf),
                    peerInfo.peerId())
            (msg as ByteBuf).release()
        }
    }

    override fun accept(packetHandler: PeerPacketHandler) {
        peerPacketHandler = packetHandler
    }

    override fun sendPacket(packet: LazyPacket) {
        //logger.debug("Sending package ---")
        context.writeAndFlush(Transport.wrapMessage(packet()))
    }

    override fun remoteAddress(): String {
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        task {
            nettyClient.shutdown()
        }
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