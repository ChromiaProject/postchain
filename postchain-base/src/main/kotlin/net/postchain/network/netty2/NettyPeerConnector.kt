// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.DefaultThreadFactory
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.network.XPacketCodec
import net.postchain.network.common.NodeConnector
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import java.util.concurrent.TimeUnit

class NettyPeerConnector<PacketType>(
        private val eventsReceiver: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor>
) : NodeConnector<PacketType, PeerConnectionDescriptor> {

    companion object : KLogging()

    private val eventLoopGroup = NioEventLoopGroup(DefaultThreadFactory("Netty"))
    private var server: NettyServer? = null

    override fun init(
            peerInfo: PeerInfo,
            packetCodec: XPacketCodec<PacketType>
    ) {
        server = NettyServer({
            NettyServerPeerConnection(packetCodec)
                    .onConnected { connection ->
                        eventsReceiver.onNodeConnected(connection)
                                ?.also { connection.accept(it) }
                    }
                    .onDisconnected { connection ->
                        eventsReceiver.onNodeDisconnected(connection)
                    }
        }, peerInfo.port, eventLoopGroup)
        logger.info { "Node started listening on messaging port ${peerInfo.port}" }
    }

    override fun connectNode(
            connectionDescriptor: PeerConnectionDescriptor,
            peerInfo: PeerInfo,
            packetCodec: XPacketCodec<PacketType>
    ) {
        with(NettyClientPeerConnection(peerInfo, packetCodec, connectionDescriptor, eventLoopGroup)) {
            try {
                open(
                        onConnected = {
                            eventsReceiver.onNodeConnected(this)
                                    ?.also { this.accept(it) }
                        },
                        onDisconnected = {
                            eventsReceiver.onNodeDisconnected(this)
                        })
            } catch (e: Exception) {
                logger.error("Netty Connect Failed, peerId: ${peerInfo.peerId()}, ${connectionDescriptor.loggingPrefix()}, with message: ${e.message}")
                eventsReceiver.onNodeDisconnected(this) // TODO: [et]: Maybe create different event receiver.
            }
        }
    }

    override fun shutdown() {
        logger.debug { "Shutting down Netty event group" }
        try {
            server?.shutdown()
            eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS).sync()
            logger.debug { "Shutting down Netty event loop group done" }
        } catch (t: Throwable) {
            logger.debug("Shutting down Netty event loop group failed", t)
        }
    }
}
