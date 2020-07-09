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
        NettySlaveConnection(masterNode, connectionDescriptor).apply {
            try {
                onConnectedHandler = {
                    eventsReceiver.onMasterConnected(connectionDescriptor, this)
                            ?.also { this.accept(it) }
                }

                onDisconnectedHandler = {
                    eventsReceiver.onMasterDisconnected(connectionDescriptor, this)
                }

                open()

            } catch (e: Exception) {
                logger.error { e.message }
                eventsReceiver.onMasterDisconnected(connectionDescriptor, this)
            }
        }
    }

    override fun shutdown() = Unit
}
