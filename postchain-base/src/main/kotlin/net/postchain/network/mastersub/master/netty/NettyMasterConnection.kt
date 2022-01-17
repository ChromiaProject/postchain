// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.postchain.core.ProgrammerMistake
import net.postchain.network.common.LazyPacket
import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.master.MasterConnection
import net.postchain.network.mastersub.master.MasterConnectionDescriptor
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsHandshakeMessage
import net.postchain.network.netty2.Transport

class NettyMasterConnection :
        ChannelInboundHandlerAdapter(),  // Make it "Netty"
        MasterConnection
{

    private lateinit var context: ChannelHandlerContext
    private var messageHandler: MsMessageHandler? = null
    private var connectionDescriptor: MasterConnectionDescriptor? = null

    var onConnectedHandler: ((MasterConnectionDescriptor, MasterConnection) -> Unit)? = null
    var onDisconnectedHandler: ((MasterConnectionDescriptor, MasterConnection) -> Unit)? = null

    override fun accept(handler: MsMessageHandler) {
        this.messageHandler = handler
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
        context.close()
    }

    // TODO: [POS-129]: Make it generic: <MsMessage> (i.e. extract MsCodec, see `NettyConnector`)
    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val messageBytes = Transport.unwrapMessage(msg as ByteBuf)
        when (val message = MsCodec.decode(messageBytes)) {
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
        ctx?.let { context = it }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        if (connectionDescriptor != null) {
            onDisconnectedHandler?.invoke(connectionDescriptor!!, this)
        }
    }

    override fun descriptor(): MasterConnectionDescriptor {
        return connectionDescriptor
                ?: throw ProgrammerMistake("Illegal to access Connection Descriptor before MsHandshakeMessage arrived.")
    }
}
