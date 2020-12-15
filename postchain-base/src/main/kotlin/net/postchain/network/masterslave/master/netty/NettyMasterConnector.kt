// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.master.netty

import mu.KLogging
import net.postchain.network.masterslave.master.MasterConnector
import net.postchain.network.masterslave.master.MasterConnectorEvents
import net.postchain.network.netty2.NettyServer

class NettyMasterConnector(
        private val eventsReceiver: MasterConnectorEvents
) : MasterConnector {

    companion object : KLogging()

    private lateinit var server: NettyServer

    // TODO: [POS-129]: Put MsCodec here
    override fun init(port: Int) {
        server = NettyServer().apply {
            setCreateChannelHandler {
                NettyMasterConnection().apply {
                    onConnectedHandler = { descriptor, connection ->
                        eventsReceiver.onSlaveConnected(descriptor, connection)
                                ?.also { connection.accept(it) }
                    }

                    onDisconnectedHandler = { descriptor, connection ->
                        eventsReceiver.onSlaveDisconnected(descriptor, connection)
                    }
                }
            }

            run(port) // TODO: [POS-129]: Change port (add the port to AppConfig)
        }
    }

    override fun shutdown() {
        server.shutdown()
    }
}
