// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode.netty

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.mastersub.subnode.SubConnectionDescriptor
import net.postchain.network.mastersub.subnode.SubConnector
import net.postchain.network.mastersub.subnode.SubConnectorEvents

class NettySubConnector(
        private val eventsReceiver: SubConnectorEvents
) : SubConnector {

    companion object : KLogging()

    override fun connectMaster(
        masterNode: PeerInfo,
        connectionDescriptor: SubConnectionDescriptor
    ) {
        val connection = NettySubConnection(masterNode, connectionDescriptor)
        try {
            connection.open(
                    onConnected = {
                        eventsReceiver.onMasterConnected(connectionDescriptor, connection)
                                ?.also { connection.accept(it) }
                    },
                    onDisconnected = {
                        eventsReceiver.onMasterDisconnected(connectionDescriptor, connection)
                    })
        } catch (e: Exception) {
            logger.error { e.message }
            eventsReceiver.onMasterDisconnected(connectionDescriptor, connection)
        }
    }

    override fun shutdown() = Unit
}
