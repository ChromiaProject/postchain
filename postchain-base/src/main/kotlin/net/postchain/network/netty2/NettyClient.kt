// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import net.postchain.core.Shutdownable
import java.net.SocketAddress

class NettyClient(
        peerAddress: SocketAddress,
        eventLoopGroup: EventLoopGroup,
        postInitChannelHandler: (ChannelPipeline) -> Unit
) : Shutdownable {

    val channelFuture: ChannelFuture

    init {
        val client = Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline()
                                .addLast(NettyCodecs.lengthFieldPrepender()) // outbound
                                .addLast(NettyCodecs.lengthFieldBasedFrameDecoder()) // inbound
                        postInitChannelHandler(ch.pipeline())
                    }
                })

        channelFuture = client.connect(peerAddress)
    }

    override fun shutdown() {
        channelFuture.channel().close().sync()
    }

    fun shutdownAsync() {
        channelFuture.channel().close()
    }
}
