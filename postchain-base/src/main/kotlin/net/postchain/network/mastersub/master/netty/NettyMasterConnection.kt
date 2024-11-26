// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.network.common.LazyPacket
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.master.MasterConnection
import net.postchain.network.mastersub.master.MasterConnectionDescriptor
import net.postchain.network.mastersub.protocol.MsCodec
import net.postchain.network.mastersub.protocol.MsHandshakeMessage
import net.postchain.network.netty2.Transport
import java.util.concurrent.CompletableFuture

class NettyMasterConnection :
        ChannelInboundHandlerAdapter(),  // Make it "Netty"
        MasterConnection {

    private lateinit var context: ChannelHandlerContext
    private var messageHandler: MsMessageHandler? = null
    private var connectionDescriptor: MasterConnectionDescriptor? = null

    var onConnectedHandler: ((MasterConnectionDescriptor, MasterConnection) -> Unit)? = null
    var onDisconnectedHandler: ((MasterConnectionDescriptor, MasterConnection) -> Unit)? = null

    private val channelInactiveFuture = CompletableFuture<Void>()

    override fun accept(handler: MsMessageHandler) {
        this.messageHandler = handler
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

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val messageBytes = Transport.unwrapMessage(msg as ByteBuf)
        try {
            when (val message = MsCodec.decode(messageBytes)) {
                is MsHandshakeMessage -> {
                    connectionDescriptor = MasterConnectionDescriptor(message.blockchainRid?.let { BlockchainRid(it) }, message.containerIID)
                    onConnectedHandler?.invoke(connectionDescriptor!!, this)
                    messageHandler?.onMessage(message)
                }

                else -> {
                    if (connectionDescriptor != null) {
                        messageHandler?.onMessage(message)
                    }
                }
            }
        } finally {
            msg.release()
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext?) {
        ctx?.let { context = it }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        channelInactiveFuture.complete(null)
        if (connectionDescriptor != null) {
            onDisconnectedHandler?.invoke(connectionDescriptor!!, this)
        }
    }

    override fun descriptor(): MasterConnectionDescriptor {
        return connectionDescriptor
                ?: throw ProgrammerMistake("Illegal to access Connection Descriptor before MsHandshakeMessage arrived.")
    }
}
