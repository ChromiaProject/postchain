package net.postchain.network.mastersub.subnode

import net.postchain.network.mastersub.MsMessageHandler

interface SubConnectorEvents {

    fun onMasterConnected(
        descriptor: SubConnectionDescriptor,
        connection: SubConnection
    ): MsMessageHandler?

    fun onMasterDisconnected(
        descriptor: SubConnectionDescriptor,
        connection: SubConnection
    )
}