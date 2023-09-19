// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master.netty

import mu.KLogging
import net.postchain.network.mastersub.master.MasterConnector
import net.postchain.network.mastersub.master.MasterConnectorEvents
import net.postchain.network.netty2.NettyServer

class NettyMasterConnector(
        private val eventsReceiver: MasterConnectorEvents,
        port: Int
) : MasterConnector {

    companion object : KLogging()

    private val server = NettyServer({
        NettyMasterConnection().apply {
            onConnectedHandler = { descriptor, connection ->
                eventsReceiver.onSubConnected(descriptor, connection)
                        ?.also { connection.accept(it) }
            }

            onDisconnectedHandler = { descriptor, connection ->
                eventsReceiver.onSubDisconnected(descriptor, connection)
            }
        }
    }, port)

    override fun shutdown() {
        server.shutdown()
    }
}
