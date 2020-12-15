package net.postchain.network.masterslave.slave

import net.postchain.base.PeerInfo
import net.postchain.core.Shutdownable
import net.postchain.network.masterslave.MsConnection
import net.postchain.network.masterslave.MsMessageHandler

interface SlaveConnector : Shutdownable {
    fun connectMaster(
            masterNode: PeerInfo,
            connectionDescriptor: SlaveConnectionDescriptor)
}

interface SlaveConnectorEvents {
    fun onMasterConnected(descriptor: SlaveConnectionDescriptor, connection: MsConnection): MsMessageHandler?
    fun onMasterDisconnected(descriptor: SlaveConnectionDescriptor, connection: MsConnection)
}

