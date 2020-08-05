// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.slave.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.masterslave.NodeConnection
import net.postchain.network.masterslave.PacketHandler
import net.postchain.network.masterslave.protocol.HandshakeMsMessage
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.masterslave.slave.SlaveConnectionDescriptor
import net.postchain.network.netty2.NettyClient
import net.postchain.network.netty2.Transport
import net.postchain.network.x.LazyPacket
import nl.komponents.kovenant.task
import java.net.InetSocketAddress
import java.net.SocketAddress

class NettySlaveConnection(
        private val masterNode: PeerInfo,
        private val connectionDescriptor: SlaveConnectionDescriptor
) : ChannelInboundHandlerAdapter(), NodeConnection {

    companion object : KLogging()

    private val nettyClient = NettyClient()
    private lateinit var context: ChannelHandlerContext
    private var packetHandler: PacketHandler? = null

    var onConnectedHandler: (() -> Unit)? = null
    var onDisconnectedHandler: (() -> Unit)? = null

    fun open() {
        nettyClient.apply {
            setChannelHandler(this@NettySlaveConnection)
            connect(masterAddress())
            if (connectFuture.isSuccess) {
                onConnectedHandler?.invoke()
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let {
            context = ctx
            context.writeAndFlush(buildHandshakePacket())
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        onDisconnectedHandler?.invoke()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val bytes = Transport.unwrapMessage(msg as ByteBuf)
        val message = MsCodec.decode(bytes)
        packetHandler?.invoke(message)
        msg.release()
    }

    override fun accept(handler: PacketHandler) {
        packetHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        val bytes = Transport.wrapMessage(packet())
        context.writeAndFlush(bytes)
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

    private fun masterAddress(): SocketAddress {
        return InetSocketAddress(masterNode.host, masterNode.port)
    }

    private fun buildHandshakePacket(): ByteBuf {
        val message = HandshakeMsMessage.build(
                connectionDescriptor.blockchainRid.data, connectionDescriptor.singers)
        val bytes = MsCodec.encode(message)
        return Transport.wrapMessage(bytes)
    }
}