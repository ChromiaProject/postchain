// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.base.PeerInfo
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder
import java.net.InetSocketAddress
import java.net.SocketAddress

interface XPeerConnection {
    fun accept(handler: XPacketHandler)
    fun sendPacket(packet: LazyPacket)
    fun remoteAddress(): String
    fun close()
    fun descriptor(): XPeerConnectionDescriptor
}

interface XConnectorEvents {
    fun onPeerConnected(connection: XPeerConnection): XPacketHandler?
    fun onPeerDisconnected(connection: XPeerConnection)
}

interface XConnector<PacketType> {
    fun init(port: Int, packetDecoder: XPacketDecoder<PacketType>): InetSocketAddress
    // TODO: [et]: Two different structures for one thing
    fun connectPeer(peerConnectionDescriptor: XPeerConnectionDescriptor, peerInfo: PeerInfo, packetEncoder: XPacketEncoder<PacketType>, peerSocketAddress: SocketAddress)
        = connectPeer(peerConnectionDescriptor, peerInfo, packetEncoder)
    fun connectPeer(peerConnectionDescriptor: XPeerConnectionDescriptor, peerInfo: PeerInfo, packetEncoder: XPacketEncoder<PacketType>)
    fun shutdown()
}

interface XConnectorFactory<PacketType> {
    fun createConnector(myPeerInfo: PeerInfo,
                        packetDecoder: XPacketDecoder<PacketType>,
                        eventReceiver: XConnectorEvents): XConnector<PacketType>
}
