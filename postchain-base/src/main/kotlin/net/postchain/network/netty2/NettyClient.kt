// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.SocketAddress

class NettyClient(private val eventLoopGroup: NioEventLoopGroup) {

    private lateinit var client: Bootstrap
    private lateinit var channelHandler: ChannelHandler

    fun setChannelHandler(channelHandler: ChannelHandler) {
        this.channelHandler = channelHandler
    }

    fun connect(peerAddress: SocketAddress): ChannelFuture {
        client = Bootstrap()
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

        return client.connect(peerAddress)
    }
}