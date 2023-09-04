package net.postchain.api.rest.controller.http4k

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import io.netty.handler.stream.ChunkedWriteHandler
import org.http4k.core.HttpHandler
import org.http4k.server.Http4kChannelHandler
import org.http4k.server.Http4kServer
import org.http4k.server.ServerConfig
import org.http4k.server.ServerConfig.StopMode
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit.MILLISECONDS

class NettyWithCustomWorkerGroup(val port: Int = 8000, override val stopMode: StopMode, private val workerGroup: NioEventLoopGroup) : ServerConfig {

    val shutdownTimeoutMillis = when (stopMode) {
        is StopMode.Graceful -> stopMode.timeout.toMillis()
        is StopMode.Immediate -> 0
    }

    override fun toServer(http: HttpHandler): Http4kServer = object : Http4kServer {
        private val masterGroup = NioEventLoopGroup()
        private var closeFuture: ChannelFuture? = null
        private lateinit var address: InetSocketAddress

        override fun start(): Http4kServer = apply {
            val bootstrap = ServerBootstrap()
            bootstrap.group(masterGroup, workerGroup)
                    .channelFactory { NioServerSocketChannel() }
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        public override fun initChannel(ch: SocketChannel) {
                            ch.pipeline().addLast("codec", HttpServerCodec())
                            ch.pipeline().addLast("keepAlive", HttpServerKeepAliveHandler())
                            ch.pipeline().addLast("aggregator", HttpObjectAggregator(Int.MAX_VALUE))
                            ch.pipeline().addLast("streamer", ChunkedWriteHandler())
                            ch.pipeline().addLast("httpHandler", Http4kChannelHandler(http))
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1000)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)

            val channel = bootstrap.bind(port).sync().channel()
            address = channel.localAddress() as InetSocketAddress
            closeFuture = channel.closeFuture()
        }

        override fun stop() = apply {
            closeFuture?.cancel(false)

            val sleepTime = minOf(2000L, shutdownTimeoutMillis)
            workerGroup.shutdownGracefully(sleepTime, shutdownTimeoutMillis, MILLISECONDS).sync()
            masterGroup.shutdownGracefully(sleepTime, shutdownTimeoutMillis, MILLISECONDS).sync()
        }

        override fun port(): Int = if (port > 0) port else address.port
    }
}
