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
        private val eventsReceiver: MasterConnectorEvents,
        port: Int
) : MasterConnector {

    private val eventLoopGroup = NioEventLoopGroup(DefaultThreadFactory("NettyMasterServer"))

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
    }, port, eventLoopGroup)

    override fun shutdown() {
        logger.debug { "Shutting down Netty event group" }
        try {
            server.shutdown()
            eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS).sync()
            logger.debug { "Shutting down Netty event loop group done" }
        } catch (t: Throwable) {
            logger.debug("Shutting down Netty event loop group failed", t)
        }
    }
}
