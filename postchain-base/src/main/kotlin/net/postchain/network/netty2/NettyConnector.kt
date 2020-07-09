// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import net.postchain.network.x.XConnector
import net.postchain.network.x.XConnectorEvents
import net.postchain.network.x.XPeerConnectionDescriptor

class NettyConnector<PacketType>(
        private val eventsReceiver: XConnectorEvents
) : XConnector<PacketType> {

    companion object : KLogging()

    private lateinit var server: NettyServer

    override fun init(peerInfo: PeerInfo, packetDecoder: XPacketDecoder<PacketType>) {
        server = NettyServer().apply {
            setCreateChannelHandler {
                NettyServerPeerConnection(packetDecoder)
                        .onConnected { descriptor, connection ->
                            eventsReceiver.onPeerConnected(descriptor, connection)
                                    ?.also { connection.accept(it) }
                        }
                        .onDisconnected { descriptor, connection ->
                            eventsReceiver.onPeerDisconnected(descriptor, connection)
                        }
            }

            run(peerInfo.port)
        }
    }

    override fun connectPeer(
            peerConnectionDescriptor: XPeerConnectionDescriptor,
            targetPeerInfo: PeerInfo,
            packetEncoder: XPacketEncoder<PacketType>
    ) {
        with(NettyClientPeerConnection(targetPeerInfo, packetEncoder)) {
            try {
                open(
                        onConnected = {
                            eventsReceiver.onPeerConnected(peerConnectionDescriptor, this)
                                    ?.also { this.accept(it) }
                        },
                        onDisconnected = {
                            eventsReceiver.onPeerDisconnected(peerConnectionDescriptor, this)
                        })
            } catch (e: Exception) {
                logger.error { e.message }
                eventsReceiver.onPeerDisconnected(peerConnectionDescriptor, this) // TODO: [et]: Maybe create different event receiver.
            }
        }
    }

    override fun shutdown() {
        server.shutdown()
    }
}
