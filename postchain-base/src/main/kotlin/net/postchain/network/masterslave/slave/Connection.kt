package net.postchain.network.masterslave.slave

import net.postchain.base.PeerInfo
import net.postchain.core.Shutdownable
import net.postchain.network.masterslave.NodeConnection
import net.postchain.network.masterslave.PacketHandler

interface SlaveConnector : Shutdownable {
    fun connectMaster(
            masterNode: PeerInfo,
            connectionDescriptor: SlaveConnectionDescriptor)
}

interface SlaveConnectorEvents {
    fun onMasterConnected(descriptor: SlaveConnectionDescriptor, connection: NodeConnection): PacketHandler?
    fun onMasterDisconnected(descriptor: SlaveConnectionDescriptor, connection: NodeConnection)
}

