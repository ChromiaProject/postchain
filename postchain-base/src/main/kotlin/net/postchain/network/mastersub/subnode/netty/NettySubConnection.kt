// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.common.LazyPacket
import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsHandshakeMessage
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.subnode.SubConnectionDescriptor
import net.postchain.network.netty2.NettyClient
import net.postchain.network.netty2.Transport
import nl.komponents.kovenant.task
import java.net.InetSocketAddress
import java.net.SocketAddress

class NettySubConnection(
        private val masterNode: PeerInfo,
        private val connectionDescriptor: SubConnectionDescriptor
) : ChannelInboundHandlerAdapter(), NodeConnection<MsMessageHandler, SubConnectionDescriptor> {

    companion object : KLogging()

    private val nettyClient = NettyClient()
    private var context: ChannelHandlerContext? = null
    private var messageHandler: MsMessageHandler? = null
    private lateinit var onDisconnected: () -> Unit

    fun open(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        this.onDisconnected = onDisconnected

        nettyClient.apply {
            setChannelHandler(this@NettySubConnection)
            val future = connect(masterAddress()).await()
            if (future.isSuccess) {
                onConnected()
            } else {
                logger.info("Connection failed", future.cause())
                onDisconnected()
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let {
            context = ctx
            context?.writeAndFlush(buildHandshakePacket())
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
        task {
            nettyClient.shutdown()
        }
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