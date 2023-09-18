// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.DefaultThreadFactory
import mu.KLogging
import net.postchain.common.exception.UserMistake
import java.net.BindException
import java.util.concurrent.TimeUnit

class NettyServer(
        createChannelHandler: () -> ChannelHandler,
        port: Int
) {

    companion object : KLogging()

    private val eventLoopGroup: EventLoopGroup

    init {
        eventLoopGroup = NioEventLoopGroup(1, DefaultThreadFactory("NettyServer"))

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
            server.bind(port).sync()
        } catch (e: BindException) {
            throw UserMistake("Unable to bind to port ${port}: ${e.message}")
        }
    }

    fun shutdown() {
        logger.debug{ "Shutting down NettyServer" }
        try {
            eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS).sync()
            logger.debug{ "Shutting down NettyServer done" }
        } catch (t: Throwable) {
            logger.debug("Shutting down NettyServer failed", t)
        }
    }
}