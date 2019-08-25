package net.postchain.network.netty2

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import net.postchain.core.Shutdownable
import java.util.concurrent.TimeUnit

class NettyServer : Shutdownable {

    private lateinit var server: ServerBootstrap
    private lateinit var bindFuture: ChannelFuture
    private lateinit var createChannelHandler: () -> ChannelHandler
    private lateinit var eventLoopGroup: EventLoopGroup
    private lateinit var workerLoopGroup: EventLoopGroup

    fun setChannelHandler(handlerFactory: () -> ChannelHandler) {
        this.createChannelHandler = handlerFactory
    }

    fun run(port: Int) {
        eventLoopGroup = NioEventLoopGroup(1)
        workerLoopGroup = NioEventLoopGroup(1)

        server = ServerBootstrap()
                .group(eventLoopGroup, workerLoopGroup)
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

        bindFuture = server.bind(port).sync()
    }

    override fun shutdown() {
        arrayOf(eventLoopGroup, workerLoopGroup)
                .map { it.shutdownGracefully() }
                .map { it.awaitUninterruptibly(1, TimeUnit.SECONDS) }
    }
}