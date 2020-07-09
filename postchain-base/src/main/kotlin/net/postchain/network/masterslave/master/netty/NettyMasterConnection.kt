// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.master.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.postchain.network.masterslave.NodeConnection
import net.postchain.network.masterslave.PacketHandler
import net.postchain.network.masterslave.master.MasterConnectionDescriptor
import net.postchain.network.masterslave.protocol.DataMsMessage
import net.postchain.network.masterslave.protocol.HandshakeMsMessage
import net.postchain.network.masterslave.protocol.MsCodec
import net.postchain.network.netty2.Transport
import net.postchain.network.x.LazyPacket

class NettyMasterConnection : ChannelInboundHandlerAdapter(), NodeConnection {

    private lateinit var context: ChannelHandlerContext
    private var packetHandler: PacketHandler? = null
    private var connectionDescriptor: MasterConnectionDescriptor? = null

    // TODO: [POS-129]: Make alias to `XPeerConnection`
    var onConnectedHandler: ((MasterConnectionDescriptor, NodeConnection) -> Unit)? = null
    var onDisconnectedHandler: ((MasterConnectionDescriptor, NodeConnection) -> Unit)? = null

    override fun accept(handler: PacketHandler) {
        this.packetHandler = handler
    }

    override fun sendPacket(packet: LazyPacket) {
        context.writeAndFlush(Transport.wrapMessage(packet()))
    }

    override fun remoteAddress(): String {
        return if (::context.isInitialized)
            context.channel().remoteAddress().toString()
        else ""
    }

    override fun close() {
        context.close()
    }

    // TODO: [POS-129]: Make it generic: <MsMessage>
    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val messageBytes = Transport.unwrapMessage(msg as ByteBuf)
        when (val message = MsCodec.decode(messageBytes)) {
            is HandshakeMsMessage -> {
                connectionDescriptor = MasterConnectionDescriptor.createFromHandshake(message)
                onConnectedHandler?.invoke(connectionDescriptor!!, this)
                packetHandler?.invoke(message)
            }

            is DataMsMessage -> {
                if (connectionDescriptor != null) {
                    // (connectionDescriptor!!.blockchainRid, ...)
                    packetHandler?.invoke(message)
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
}
