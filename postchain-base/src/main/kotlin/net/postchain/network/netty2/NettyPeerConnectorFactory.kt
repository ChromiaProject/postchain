// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

/**
 * (Not much code here, just some type parameter magic)
 */

/* (Maybe I'm wrong removing this, b/c it might have a conceptual use I don't yet understand)
class NettyPeerConnectorFactory<PacketType> :
    NodeConnectorFactory<PacketType, PeerPacketHandler, PeerConnectionDescriptor> {

    override fun createConnector(
        myPeerInfo: PeerInfo,
        packetDecoder: XPacketDecoder<PacketType>,
        eventReceiver: ConnectionLifecycle<PeerPacketHandler, PeerConnectionDescriptor>
    ): NodeConnector<PacketType, PeerConnectionDescriptor> {

        return NettyPeerConnector<PacketType>(eventReceiver).apply {
            init(myPeerInfo, packetDecoder)
        }
    }
}
 */