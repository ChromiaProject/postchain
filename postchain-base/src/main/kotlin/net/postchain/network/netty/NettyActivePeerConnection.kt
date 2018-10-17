package net.postchain.network.ref.netty

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.IdentPacketConverter
import net.postchain.network.MAX_PAYLOAD_SIZE
import java.net.InetSocketAddress
import kotlin.concurrent.thread

/**
 * ruslan.klymenko@zorallabs.com 04.10.18
 */
class NettyActivePeerConnection(val peerInfo: PeerInfo,
                                val packetConverter: IdentPacketConverter,
                                val packetHandler: (ByteArray) -> Unit): NettyPeerConnection() {


    val group = NioEventLoopGroup()

    override fun handlePacket(pkt: ByteArray) {
        packetHandler(pkt)
    }

    override fun stop() {
        group.shutdownGracefully().sync()
    }

    companion object : KLogging()



    fun start() {
        Thread{ createClient(peerInfo)}.start()
    }

    private fun createClient(peerInfo: PeerInfo) {
        try {
            val clientBootstrap = Bootstrap()
            clientBootstrap.group(group)
            clientBootstrap.channel(NioSocketChannel::class.java)
            clientBootstrap.remoteAddress(InetSocketAddress(peerInfo.host, peerInfo.port))
            clientBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(socketChannel: SocketChannel) {
                    socketChannel.pipeline()
                            .addLast(LengthFieldBasedFrameDecoder(MAX_PAYLOAD_SIZE, 0, packetSizeLength, 0, 0))
                            //.addLast(DelimiterBasedFrameDecoder(MAX_PAYLOAD_SIZE, Unpooled.copiedBuffer(delimiter.toByteArray())))
                            .addLast(ClientHandler(packetConverter, peerInfo))
                }
            })
            val channelFuture = clientBootstrap.connect().sync()
            channelFuture.channel().closeFuture().sync()
        } catch (e: Exception) {
            logger.error(e.toString())
         }
    }

    inner class ClientHandler(val packetConverter: IdentPacketConverter,
                        val peerInfo: PeerInfo): SimpleChannelInboundHandler<Any>() {

        override fun channelActive(channelHandlerContext: ChannelHandlerContext) {
            val identPacket = packetConverter.makeIdentPacket(peerInfo.pubKey)
            writeOnePacket(channelHandlerContext, identPacket)
            ctx = channelHandlerContext
        }
        override fun exceptionCaught(channelHandlerContext: ChannelHandlerContext, cause: Throwable) {
            channelHandlerContext.close()
        }
        override fun channelRead0(channelHandlerContext: ChannelHandlerContext, o: Any) {
            val bytes = readOnePacket(o)
            handlePacket(bytes)
        }
    }
}

