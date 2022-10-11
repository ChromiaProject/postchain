// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master.netty

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.DefaultThreadFactory
import mu.KLogging
import net.postchain.network.mastersub.master.MasterConnector
import net.postchain.network.mastersub.master.MasterConnectorEvents
import net.postchain.network.netty2.NettyServer
import java.util.concurrent.TimeUnit

class NettyMasterConnector(
        private val eventsReceiver: MasterConnectorEvents
) : MasterConnector {

    companion object : KLogging()

    private val eventLoopGroup = NioEventLoopGroup(1, DefaultThreadFactory("NettyServerMaster"))
    private lateinit var server: NettyServer

    // TODO: [POS-129]: Put MsCodec here
    override fun init(port: Int) {
        server = NettyServer(eventLoopGroup).apply {
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

            run(port) // TODO: [POS-129]: Change port (add the port to AppConfig)
        }
    }

    override fun shutdown() {
        eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS).sync()
    }
}
