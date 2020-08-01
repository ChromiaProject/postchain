package net.postchain.network.masterslave.master

import net.postchain.core.Shutdownable
import net.postchain.network.masterslave.NodeConnection
import net.postchain.network.masterslave.PacketHandler

interface MasterConnector<PacketType> : Shutdownable {
    fun init(port: Int/*, packetDecoder: XPacketDecoder<PacketType>*/)
}

interface MasterConnectorEvents {
    fun onSlaveConnected(descriptor: MasterConnectionDescriptor, connection: NodeConnection): PacketHandler?
    fun onSlaveDisconnected(descriptor: MasterConnectionDescriptor, connection: NodeConnection)
}
