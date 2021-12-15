// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.master.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KLogging
import net.postchain.network.masterslave.MsConnection
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.master.MasterConnectionDescriptor
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.masterslave.protocol.MsHandshakeMessage
import net.postchain.network.netty2.Transport
import net.postchain.network.x.LazyPacket

class NettyMasterConnection : ChannelInboundHandlerAdapter(), MsConnection {

    private lateinit var context: ChannelHandlerContext
    private var messageHandler: MsMessageHandler? = null
    private var connectionDescriptor: MasterConnectionDescriptor? = null

    var onConnectedHandler: ((MasterConnectionDescriptor, MsConnection) -> Unit)? = null
    var onDisconnectedHandler: ((MasterConnectionDescriptor, MsConnection) -> Unit)? = null

    companion object : KLogging()

    override fun accept(handler: MsMessageHandler) {
        logger.debug("accept() - ")
        this.messageHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        logger.debug("sendPacket() - ")
        context.writeAndFlush(Transport.wrapMessage(packet()))
    }

    override fun remoteAddress(): String {
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        logger.debug("close() - ")
        context.close()
    }

    // TODO: [POS-129]: Make it generic: <MsMessage> (i.e. extract MsCodec, see `NettyConnector`)
    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val messageBytes = Transport.unwrapMessage(msg as ByteBuf)
        val message = MsCodec.decode(messageBytes)
        logger.debug("channelRead() - message: ${message.type}")
        when (message) {
            is MsHandshakeMessage -> {
                connectionDescriptor = MasterConnectionDescriptor.createFromHandshake(message)
                onConnectedHandler?.invoke(connectionDescriptor!!, this)
                messageHandler?.onMessage(message)
            }

            else -> {
                if (connectionDescriptor != null) {
                    messageHandler?.onMessage(message)
                }
            }
        }

        msg.release()
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        logger.debug("channelActive() - ")
        ctx?.let { context = it }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        logger.debug("channelInactive() - ")
        if (connectionDescriptor != null) {
            onDisconnectedHandler?.invoke(connectionDescriptor!!, this)
        }
    }
}
