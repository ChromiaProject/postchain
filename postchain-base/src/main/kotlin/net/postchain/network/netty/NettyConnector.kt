package net.postchain.network.netty

import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import net.postchain.base.CryptoSystem
import net.postchain.base.PeerInfo
import net.postchain.network.*
import net.postchain.network.x.XConnector
import net.postchain.network.x.XConnectorEvents
import net.postchain.network.x.XPeerConnectionDescriptor

class NettyConnector(private val myPeerInfo: PeerInfo,
                     private val eventReceiver: XConnectorEvents,
                     private val identPacketConverter: IdentPacketConverter,
                     private val cryptoSystem: CryptoSystem): XConnector {

    val group: EventLoopGroup
    val serverChannel: Class<ServerSocketChannel>
    val clientChannel: Class<SocketChannel>
    init {
        if(System.getProperty("os.name").toLowerCase().contains("linux")) {
            group = EpollEventLoopGroup()
            serverChannel = EpollServerSocketChannel::class.java as Class<ServerSocketChannel>
            clientChannel = EpollSocketChannel::class.java as Class<SocketChannel>
        } else {
            group = NioEventLoopGroup()
            serverChannel = NioServerSocketChannel::class.java as Class<ServerSocketChannel>
            clientChannel = NioSocketChannel::class.java as Class<SocketChannel>
        }
        NettyPassivePeerConnection(myPeerInfo, identPacketConverter, eventReceiver, serverChannel, group, cryptoSystem)
    }

    override fun connectPeer(descriptor: XPeerConnectionDescriptor, peerInfo: PeerInfo) {
        NettyActivePeerConnection(peerInfo, descriptor, identPacketConverter, eventReceiver, clientChannel, group, cryptoSystem)
    }

    override fun shutdown() {
        group.shutdownGracefully().sync()
    }


}