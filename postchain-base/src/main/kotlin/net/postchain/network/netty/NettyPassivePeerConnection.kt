package net.postchain.network.ref.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import net.postchain.base.PeerInfo
import net.postchain.network.IdentPacketConverter
import net.postchain.network.IdentPacketInfo
import java.net.InetSocketAddress
import kotlin.concurrent.thread

/**
 * ruslan.klymenko@zorallabs.com 04.10.18
 */
class NettyPassivePeerConnection (
        val peerInfo: PeerInfo,
        val packetConverter: IdentPacketConverter,
        val registerConn: (info: IdentPacketInfo, NettyPeerConnection) -> (ByteArray) -> Unit
) : NettyPeerConnection() {

    lateinit var packetHandler: (ByteArray) -> Unit

    override fun handlePacket(pkt: ByteArray) {
        packetHandler(pkt)
    }

    init {
        Thread({createServer()}).start()
    }

    private fun createServer() {
        val group = NioEventLoopGroup()

        try {
            val serverBootstrap = ServerBootstrap()
            serverBootstrap.group(group)
            serverBootstrap.channel(NioServerSocketChannel::class.java)
            serverBootstrap.localAddress(InetSocketAddress(peerInfo.host, peerInfo.port))
         //   LengthFieldBasedFrameDecoder()
            val that = this
            serverBootstrap.childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(socketChannel: SocketChannel) {
                    socketChannel.pipeline()
                           // .addLast(DelimiterBasedFrameDecoder(8192, *Delimiters.lineDelimiter()))
                            .addLast(ServerHandler(that))
                }
            })
            val channelFuture = serverBootstrap.bind().sync()
            channelFuture.channel().closeFuture().sync()
        } catch (e: Exception) {
            logger.error(e.toString())
        } finally {
            group.shutdownGracefully().sync()
        }
    }

    inner class ServerHandler(val peerConnection: NettyPeerConnection): ChannelInboundHandlerAdapter() {

        @Volatile
        private var identified = false
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if(!identified) {
                synchronized(identified) {
                    if (!identified) {
                        val info = packetConverter.parseIdentPacket(readOnePacket(msg))
                        packetHandler = registerConn(info, peerConnection)
                        identified = true
                    }
                    thread(name = "PassiveWriteLoop-PeerId") {
                        writePacketsWhilePossible(ctx)
                    }
                }
            }
            val bytes = readOnePacket(msg)
            handlePacket(bytes)
        }
        override fun channelReadComplete(ctx: ChannelHandlerContext) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
               //     .addListener(ChannelFutureListener.CLOSE)
        }
        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
          //  stop()
            ctx.close()
        }
    }

}