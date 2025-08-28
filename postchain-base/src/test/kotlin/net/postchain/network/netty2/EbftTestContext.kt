// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import io.netty.channel.ChannelPipeline
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.ebft.EbftPacketCodec
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.XPacketCodec
import net.postchain.network.common.NodeConnection
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import java.util.Collections

class EbftTestContext(val config: PeerCommConfiguration, val blockchainRid: BlockchainRid) {

    val connectionConfig = ConnectionConfig()
    var readHandshakeTimeoutHandler = true

    val packets: PeerPacketHandler = mock()
    val connections: MutableCollection<NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>> =
            Collections.synchronizedList(mutableListOf<NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>>())
    val serverConnections: MutableCollection<NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>> =
            Collections.synchronizedList(mutableListOf<NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>>())

    val events = spy(object : NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor> {

        override fun onNodeConnected(connection: NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>): PeerPacketHandler {
            connections.add(connection)
            return packets
        }

        override fun onNodeDisconnected(connection: NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>) {
            connections.remove(connection)
        }
    })

    private val serverChannelHandlerFactory = object : ServerChannelHandlerFactory {

        override fun <PacketType> onPostInitChannelHandler(pipeline: ChannelPipeline, packetCodec: XPacketCodec<PacketType>, eventsReceiver: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor>) {
            // The default initialisation flow
            DefaultServerChannelHandlerFactory(connectionConfig).onPostInitChannelHandler(pipeline, packetCodec, eventsReceiver)

            // Get the connectionHandler and add it to the `serverConnections` list
            serverConnections.add(pipeline.last() as NettyServerPeerConnection<*>)

            // Remove the ReadHandshakeTimeoutHandler
            if (!readHandshakeTimeoutHandler) {
                pipeline.remove(ReadHandshakeTimeoutHandler::class.java)
            }
        }
    }

    val connector = NettyPeerConnector<EbftMessage>(events, connectionConfig, serverChannelHandlerFactory)

    fun init() = connector.init(config.myPeerInfo(), EbftPacketCodec(config, blockchainRid))

    fun buildPacketCodec(): EbftPacketCodec = EbftPacketCodec(config, blockchainRid)

    fun encodePacket(message: EbftMessage, version: Long): ByteArray = buildPacketCodec().encodePacket(message, version)

    fun decodePacket(bytes: ByteArray, version: Long): EbftMessage = buildPacketCodec().decodePacket(bytes, version)!!

    fun shutdown() = connector.shutdown()
}