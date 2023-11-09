// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import mu.KLogging
import net.postchain.common.exception.UserMistake
import java.net.BindException

class NettyServer(
        createChannelHandler: () -> ChannelHandler,
        port: Int,
        eventLoopGroup: EventLoopGroup
) {

    private var channelFuture: ChannelFuture

    companion object : KLogging()

    init {
        val server = ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel::class.java)
//                .option(ChannelOption.SO_BACKLOG, 10)
//                .handler(LoggingHandler(LogLevel.INFO))
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline()
                                // inbound
                                .addLast(NettyCodecs.lengthFieldPrepender())
                                // outbound
                                .addLast(NettyCodecs.lengthFieldBasedFrameDecoder())
                                // app
                                .addLast(createChannelHandler())
                    }
                })

        try {
            channelFuture = server.bind(port).sync()
        } catch (e: BindException) {
            throw UserMistake("Unable to bind to port ${port}: ${e.message}")
        }
    }

    fun shutdown() {
        channelFuture.channel().close().sync()
    }
}
