package net.postchain.network.masterslave.master

import net.postchain.core.Shutdownable
import net.postchain.network.masterslave.MsConnection
import net.postchain.network.masterslave.MsMessageHandler

interface MasterConnector : Shutdownable {
    fun init(port: Int)
}

interface MasterConnectorEvents {
    fun onSlaveConnected(descriptor: MasterConnectionDescriptor, connection: MsConnection): MsMessageHandler?
    fun onSlaveDisconnected(descriptor: MasterConnectionDescriptor, connection: MsConnection)
}
