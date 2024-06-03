package net.postchain.network.netty2

import io.netty.channel.ChannelPipeline
import net.postchain.network.XPacketCodec
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler

interface ServerChannelHandlerFactory {

    fun <PacketType> onPostInitChannelHandler(
            pipeline: ChannelPipeline,
            packetCodec: XPacketCodec<PacketType>,
            eventsReceiver: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor>
    )
}

class DefaultServerChannelHandlerFactory(
        private val connectionConfig: ConnectionConfig
) : ServerChannelHandlerFactory {

    override fun <PacketType> onPostInitChannelHandler(
            pipeline: ChannelPipeline,
            packetCodec: XPacketCodec<PacketType>,
            eventsReceiver: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor>
    ) {

        // the main connection handler
        val connectionHandler = NettyServerPeerConnection(packetCodec).apply {
            onConnected { connection -> eventsReceiver.onNodeConnected(connection)?.also { connection.accept(it) } }
            onDisconnected(eventsReceiver::onNodeDisconnected)
        }

        // bind readHandshakeTimeoutHandler to the connection state
        if (connectionConfig.readHandshakeTimeout > 0) {
            val readHandshakeTimeoutHandler = ReadHandshakeTimeoutHandler(connectionConfig) { connectionHandler.isConnected }
            pipeline.addFirst(readHandshakeTimeoutHandler)
        }

        pipeline.addLast(connectionHandler)
    }
}
