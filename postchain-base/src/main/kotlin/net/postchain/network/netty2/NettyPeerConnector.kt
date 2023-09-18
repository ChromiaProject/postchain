// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.common.NodeConnector
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler

class NettyPeerConnector<PacketType>(
        private val eventsReceiver: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor>
) : NodeConnector<PacketType, PeerConnectionDescriptor> {

    companion object : KLogging()

    private lateinit var server: NettyServer

    override fun init(peerInfo: PeerInfo, packetDecoder: XPacketDecoder<PacketType>) {
        server = NettyServer({
            NettyServerPeerConnection(packetDecoder)
                    .onConnected { connection ->
                        eventsReceiver.onNodeConnected(connection)
                                ?.also { connection.accept(it) }
                    }
                    .onDisconnected { connection ->
                        eventsReceiver.onNodeDisconnected(connection)
                    }
        }, peerInfo.port)
        logger.info { "Node started listening on messaging port ${peerInfo.port}" }
    }

    override fun connectNode(
            connectionDescriptor: PeerConnectionDescriptor,
            peerInfo: PeerInfo,
            packetEncoder: XPacketEncoder<PacketType>
    ) {
        with(NettyClientPeerConnection(peerInfo, packetEncoder, connectionDescriptor)) {
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
        server.shutdown()
    }
}
