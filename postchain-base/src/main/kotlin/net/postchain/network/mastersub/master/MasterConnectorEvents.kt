package net.postchain.network.mastersub.master

import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler

interface MasterConnectorEvents {

    fun onSubConnected(
        descriptor: MasterConnectionDescriptor,
        connection: NodeConnection<MsMessageHandler, MasterConnectionDescriptor>
    ): MsMessageHandler?

    fun onSubDisconnected(
        descriptor: MasterConnectionDescriptor,
        connection: NodeConnection<MsMessageHandler, MasterConnectionDescriptor>
    )
}