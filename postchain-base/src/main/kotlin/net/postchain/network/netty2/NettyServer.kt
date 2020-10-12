// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import net.postchain.base.DynamicPortPeerInfo
import net.postchain.base.PeerInfo
import net.postchain.core.Shutdownable
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class NettyServer : Shutdownable {

    private lateinit var server: ServerBootstrap
    private lateinit var bindFuture: ChannelFuture
    private lateinit var createChannelHandler: () -> ChannelHandler
    private lateinit var eventLoopGroup: EventLoopGroup

    fun setChannelHandler(handlerFactory: () -> ChannelHandler) {
        this.createChannelHandler = handlerFactory
    }

    fun run(peerInfo : PeerInfo) {
        eventLoopGroup = NioEventLoopGroup(1)

        server = ServerBootstrap()
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

        if (peerInfo is DynamicPortPeerInfo) {
            bindFuture = server.bind(0).sync()
            val channel = bindFuture.channel()
            // I don't know if there's a more elegant way to get the assigned port
            val p = (channel.localAddress() as InetSocketAddress).port
            peerInfo.portAssigned(p)
        } else {
            bindFuture = server.bind(peerInfo.port).sync()
        }
    }

    override fun shutdown() {
//        bindFuture.channel().close().sync()
//        bindFuture.channel().closeFuture().sync()
        // TODO: [et]: Fix this, make it .sync() again
        eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS)//.sync()
        //.syncUninterruptibly()
    }
}