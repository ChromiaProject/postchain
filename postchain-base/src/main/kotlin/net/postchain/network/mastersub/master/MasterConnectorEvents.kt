package net.postchain.network.mastersub.master

import net.postchain.network.mastersub.MsMessageHandler

interface MasterConnectorEvents {

    fun onSubConnected(
        descriptor: MasterConnectionDescriptor,
        connection: MasterConnection
    ): MsMessageHandler?

    fun onSubDisconnected(
        descriptor: MasterConnectionDescriptor,
        connection: MasterConnection
    )
}