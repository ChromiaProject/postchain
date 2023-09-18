// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.DefaultThreadFactory
import net.postchain.core.Shutdownable
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

class NettyClient(
        channelHandler: ChannelHandler,
        peerAddress: SocketAddress
) : Shutdownable {

    private val eventLoopGroup: EventLoopGroup
    val channelFuture: ChannelFuture

    init {
        eventLoopGroup = NioEventLoopGroup(1, DefaultThreadFactory("NettyClient"))

        val client = Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline()
                                // inbound
                                .addLast(NettyCodecs.lengthFieldPrepender())
                                // outbound
                                .addLast(NettyCodecs.lengthFieldBasedFrameDecoder())
                                // app
                                .addLast(channelHandler)
                    }
                })

        channelFuture = client.connect(peerAddress)
    }

    override fun shutdown() {
        eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS).sync()
    }

    fun shutdownAsync() {
        eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS)
    }
}