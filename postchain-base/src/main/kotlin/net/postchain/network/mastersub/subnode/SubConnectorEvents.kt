package net.postchain.network.mastersub.subnode

import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler

interface SubConnectorEvents {

    fun onMasterConnected(
        descriptor: SubConnectionDescriptor,
        connection: NodeConnection<MsMessageHandler, SubConnectionDescriptor>
    ): MsMessageHandler?

    fun onMasterDisconnected(
        descriptor: SubConnectionDescriptor,
        connection: NodeConnection<MsMessageHandler, SubConnectionDescriptor>
    )
}