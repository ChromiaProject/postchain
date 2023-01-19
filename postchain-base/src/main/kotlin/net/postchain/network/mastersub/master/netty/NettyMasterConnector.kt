// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master.netty

import mu.KLogging
import net.postchain.network.mastersub.master.MasterConnector
import net.postchain.network.mastersub.master.MasterConnectorEvents
import net.postchain.network.netty2.NettyServer

class NettyMasterConnector(
        private val eventsReceiver: MasterConnectorEvents
) : MasterConnector {

    companion object : KLogging()

    private lateinit var server: NettyServer

    override fun init(port: Int) {
        server = NettyServer().apply {
            setCreateChannelHandler {
                NettyMasterConnection().apply {
                    onConnectedHandler = { descriptor, connection ->
                        eventsReceiver.onSubConnected(descriptor, connection)
                                ?.also { connection.accept(it) }
                    }

                    onDisconnectedHandler = { descriptor, connection ->
                        eventsReceiver.onSubDisconnected(descriptor, connection)
                    }
                }
            }

            run(port)
        }
    }

    override fun shutdown() {
        server.shutdown()
    }
}
