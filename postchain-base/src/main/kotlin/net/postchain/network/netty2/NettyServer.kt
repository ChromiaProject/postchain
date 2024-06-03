// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import mu.KLogging
import net.postchain.common.exception.UserMistake
import java.net.BindException

class NettyServer(
        port: Int,
        eventLoopGroup: EventLoopGroup,
        postInitChannelHandler: (ChannelPipeline) -> Unit
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
                                .addLast(NettyCodecs.lengthFieldPrepender()) // outbound
                                .addLast(NettyCodecs.lengthFieldBasedFrameDecoder()) // inbound
                        postInitChannelHandler(ch.pipeline())
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
