// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.base.PeerInfo
import net.postchain.core.Shutdownable
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder

interface XPeerConnection {
    fun accept(handler: XPacketHandler)
    fun sendPacket(packet: LazyPacket)
    fun remoteAddress(): String
    fun close()
}

interface XConnectorEvents {
    fun onPeerConnected(descriptor: XPeerConnectionDescriptor, connection: XPeerConnection): XPacketHandler?
    fun onPeerDisconnected(descriptor: XPeerConnectionDescriptor, connection: XPeerConnection)
}

interface XConnector<PacketType> : Shutdownable {
    fun init(peerInfo: PeerInfo, packetDecoder: XPacketDecoder<PacketType>)
    // TODO: [et]: Two different structures for one thing
    fun connectPeer(
            peerConnectionDescriptor: XPeerConnectionDescriptor,
            targetPeerInfo: PeerInfo,
            packetEncoder: XPacketEncoder<PacketType>)
}

interface XConnectorFactory<PacketType> {
    fun createConnector(myPeerInfo: PeerInfo,
                        packetDecoder: XPacketDecoder<PacketType>,
                        eventReceiver: XConnectorEvents): XConnector<PacketType>
}
