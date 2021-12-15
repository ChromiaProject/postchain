// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.slave.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.MsConnection
import net.postchain.network.masterslave.protocol.MsHandshakeMessage
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
) : ChannelInboundHandlerAdapter(), MsConnection {

    companion object : KLogging()

    private val nettyClient = NettyClient()
    private lateinit var context: ChannelHandlerContext
    private var messageHandler: MsMessageHandler? = null
    private lateinit var onDisconnected: () -> Unit

    fun open(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        logger.info("open() - begin")
        this.onDisconnected = onDisconnected

        nettyClient.apply {
            setChannelHandler(this@NettySlaveConnection)
            val future = connect(masterAddress()).await()
            if (future.isSuccess) {
                onConnected()
            } else {
                logger.info("Connection failed", future.cause())
                onDisconnected()
            }
        }

        logger.info("open() - end")
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        logger.debug("channelActive() - begin")
        ctx?.let {
            context = ctx
            context.writeAndFlush(buildHandshakePacket())
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        logger.debug("channelInactive() - begin")
        onDisconnected()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        logger.debug("channelRead() - begin")
        val bytes = Transport.unwrapMessage(msg as ByteBuf)
        val message = MsCodec.decode(bytes)
        messageHandler?.onMessage(message)
        msg.release()
    }

    override fun accept(handler: MsMessageHandler) {
        logger.debug("accept() - begin")
        messageHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        logger.debug("sendPacket() - begin")
        val bytes = Transport.wrapMessage(packet())
        context.writeAndFlush(bytes)
    }

    override fun remoteAddress(): String {
        logger.debug("remoteAddress() - begin")
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        logger.debug("close() - begin")
        task {
            nettyClient.shutdown()
        }
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