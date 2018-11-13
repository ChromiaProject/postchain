package net.postchain.network.netty

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import net.postchain.base.*
import net.postchain.network.*
import net.postchain.network.netty.bc.SymmetricEncryptorUtil
import net.postchain.network.x.*
import net.postchain.network.x.XConnectorEvents
import net.postchain.network.x.XPeerConnection
import net.postchain.network.x.XPeerConnectionDescriptor
import java.net.InetSocketAddress

class NettyActivePeerConnection(private val remotePeerInfo: PeerInfo,
                                private val myPeerInfo: PeerInfo,
                                private val descriptor: XPeerConnectionDescriptor,
                                private val identPacketConverter: IdentPacketConverter,
                                private val eventReceiver: XConnectorEvents,
                                private val channelClass: Class<SocketChannel>,
                                eventLoopGroup: EventLoopGroup,
                                cryptoSystem: CryptoSystem,
                                private val enabledEncryption: Boolean): NettyIO(eventLoopGroup, cryptoSystem) {

    override fun startSocket() {
        try {
            val clientBootstrap = Bootstrap()
            clientBootstrap.group(group)
            clientBootstrap.channel(channelClass)
            clientBootstrap.remoteAddress(InetSocketAddress(remotePeerInfo.host, remotePeerInfo.port))
            clientBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(socketChannel: SocketChannel) {
                    socketChannel.pipeline()
                            .addLast(NettyIO.framePrepender)
                            .addLast(LengthFieldBasedFrameDecoder(MAX_PAYLOAD_SIZE, 0, packetSizeLength, 0, packetSizeLength))
                            .addLast(ClientHandler())
                }
            })
            val channelFuture = clientBootstrap.connect().sync()
            channelFuture.channel().closeFuture().sync()
        } catch (e: Exception) {
            logger.error(e.toString())
        }
    }

    inner class ClientHandler : SimpleChannelInboundHandler<Any>(), XPeerConnection {
        private val ephemeralKey = SECP256K1CryptoSystem().getRandomBytes(keySizeBytes)
        private val ephemeralPubKey = secp256k1_derivePubKey(ephemeralKey)
        private lateinit var handler: XPacketHandler
        private lateinit var sessionKey: ByteArray
        private lateinit var ctx: ChannelHandlerContext
        var messagesSent = 0L
        private set

        override fun close() {
            ctx?.close()
        }

        override fun accept(handler: XPacketHandler) {
            this.handler = handler
        }

        override fun sendPacket(packet: LazyPacket) {
            if(ctx != null) {
                val message = if(enabledEncryption) SymmetricEncryptorUtil.encrypt(packet.invoke(), sessionKey, ++messagesSent)
                                    else packet.invoke()
                ctx!!.writeAndFlush(Unpooled.wrappedBuffer(message), ctx!!.voidPromise())
            }
        }

        fun sendIdentPacket(packet: LazyPacket) {
            if(ctx != null) {
                ctx!!.writeAndFlush(Unpooled.wrappedBuffer(packet.invoke()), ctx!!.voidPromise())
            }
        }


        override fun channelActive(channelHandlerContext: ChannelHandlerContext) {
            ctx = channelHandlerContext
            generateSessionKey(remotePeerInfo.pubKey)
            val identPacket = identPacketConverter.makeIdentPacket(descriptor, ephemeralPubKey, myPeerInfo.pubKey)
            sendIdentPacket({ identPacket })
            accept(eventReceiver.onPeerConnected(descriptor, this)!!)
        }


        override fun exceptionCaught(channelHandlerContext: ChannelHandlerContext, cause: Throwable) {
            eventReceiver.onPeerDisconnected(descriptor)
            close()
        }
        fun readPacket(msg: Any) = NettyIO.readPacket(msg)

        override fun channelRead0(channelHandlerContext: ChannelHandlerContext, o: Any) {
            handleInput(o)
        }

        private fun generateSessionKey(remotePubKey: ByteArray) {
            val ecdh1 = secp256k1_ecdh(myPeerInfo.privateKey!!, remotePubKey)
            val ecdh2 = secp256k1_ecdh(ephemeralKey, remotePubKey)
            val digest = cryptoSystem.digest(ecdh1 + ecdh2)
            sessionKey = digest
        }

        fun readEncryptedPacket(msg: Any): ByteArray {
            val bytes = readPacket(msg)
            return if(bytes.isEmpty()) bytes
            else if(enabledEncryption) SymmetricEncryptorUtil.decrypt(bytes, sessionKey)!!.byteArray
                 else bytes
        }

        private fun handleInput(o: Any) {
            if(handler != null) {
                val bytes = readEncryptedPacket(o)
                if(bytes.isNotEmpty()) {
                    handler!!.invoke(bytes, descriptor.peerID)
                }
            } else {
                logger.error("${this::class.java.name}, handler is null")
            }
        }
    }
}