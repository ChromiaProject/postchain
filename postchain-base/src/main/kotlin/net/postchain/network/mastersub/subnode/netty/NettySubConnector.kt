// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode.netty

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.DefaultThreadFactory
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.network.mastersub.subnode.SubConnectionDescriptor
import net.postchain.network.mastersub.subnode.SubConnector
import net.postchain.network.mastersub.subnode.SubConnectorEvents
import java.util.concurrent.TimeUnit

class NettySubConnector(
        private val eventsReceiver: SubConnectorEvents,
) : SubConnector {

    companion object : KLogging()

    private val eventLoopGroup = NioEventLoopGroup(DefaultThreadFactory("NettySubClient"))

    override fun connectMaster(
        masterNode: PeerInfo,
        connectionDescriptor: SubConnectionDescriptor
    ) {
        val connection = NettySubConnection(masterNode, connectionDescriptor, eventLoopGroup)
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

    override fun shutdown() {
        logger.debug { "Shutting down Netty event group" }
        try {
            eventLoopGroup.shutdownGracefully(0, 2000, TimeUnit.MILLISECONDS).sync()
            logger.debug { "Shutting down Netty event loop group done" }
        } catch (t: Throwable) {
            logger.debug("Shutting down Netty event loop group failed", t)
        }
    }
}
