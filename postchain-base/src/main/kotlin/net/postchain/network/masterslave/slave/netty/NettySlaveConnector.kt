// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.slave.netty

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.masterslave.slave.SlaveConnectionDescriptor
import net.postchain.network.masterslave.slave.SlaveConnector
import net.postchain.network.masterslave.slave.SlaveConnectorEvents

class NettySlaveConnector(
        private val eventsReceiver: SlaveConnectorEvents
) : SlaveConnector {

    companion object : KLogging()

    override fun connectMaster(masterNode: PeerInfo, connectionDescriptor: SlaveConnectionDescriptor) {
        logger.info("connectMaster() - begin")
        val connection = NettySlaveConnection(masterNode, connectionDescriptor)
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
        logger.info("connectMaster() - end")
    }

    override fun shutdown() = Unit
}
