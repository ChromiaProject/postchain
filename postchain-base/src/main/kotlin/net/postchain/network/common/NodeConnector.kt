// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.common

import net.postchain.base.PeerInfo
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder

/**
 * A "Node Connector" is wrapper around the underlying network library we use (for example: Netty).
 * Apart from startup and shutdown, it can only do one thin: connect a node.
 *
 * @property PacketType is the type of message class
 * @property DescriptorType is the type that handle the connection's info
 */
interface NodeConnector<PacketType, DescriptorType> {

    /**
     * Initiates the network implementation (ex: Netty) with connection life cycle logic
     */
    fun init(peerInfo: PeerInfo, packetDecoder: XPacketDecoder<PacketType>)

    /**
     * Makes sure the network implementation (ex: Netty) connects to the given node
     */
    fun connectNode(
        connectionDescriptor: DescriptorType,
        peerInfo: PeerInfo,
        packetEncoder: XPacketEncoder<PacketType>
    )

    /**
     * Shuts down the network implementation (ex: Netty).
     */
    fun shutdown()

}

/**
 * TODO: Olle: Is this only to hide that we are using Netty? If so, why isn't this also used by [DefaultMasterConnectionManager]
 */
/* (Maybe I'm wrong removing this, b/c it might have a conceptual use I don't yet understand)
interface NodeConnectorFactory<PacketType, HandlerType, DescriptorType> {
    fun createConnector(
            myPeerInfo: PeerInfo,
            packetDecoder: XPacketDecoder<PacketType>,
            eventReceiver: ConnectionLifecycle<HandlerType, DescriptorType>
    ): NodeConnector<PacketType, DescriptorType>
}
 */
