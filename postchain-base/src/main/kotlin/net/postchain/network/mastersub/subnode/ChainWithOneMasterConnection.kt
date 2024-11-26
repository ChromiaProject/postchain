package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.network.common.ChainWithOneConnection
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsDataMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.peer.XChainPeersConfiguration
import java.util.concurrent.CompletableFuture


/**
 * A chain running on a sub node only has one connection: to the master.
 */
class ChainWithOneMasterConnection(
        val config: XChainPeersConfiguration
) : ChainWithOneConnection<SubConnection, MsMessageHandler> {

    companion object : KLogging()

    val peers: List<ByteArray> = (config.commConfiguration as SubPeerCommConfig).peers
    private val msMessageHandlers = mutableListOf<MsMessageHandler>()
    private val msMessagePipeline: MsMessageHandler = object : MsMessageHandler {
        override fun onMessage(message: MsMessage) {
            when (message) {
                is MsDataMessage -> config.peerPacketHandler.handle(message.xPacket, NodeRid(message.source))
                else -> {
                    msMessageHandlers.forEach { h -> h.onMessage(message) }
                }
            }
        }
    }

    // Maximum ONE connection: to the master
    var conn: SubConnection? = null


    // ---------------------------
    // Trivial impl of the interface
    // ----------------------------
    override fun getChainIid() = config.chainId
    override fun getBlockchainRid() = config.blockchainRid
    override fun getPacketHandler() = msMessagePipeline
    override fun isConnected() = conn != null
    override fun getConnection() = conn
    override fun setConnection(newConn: SubConnection) {
        conn = newConn
    }

    override fun closeConnection(): CompletableFuture<Void>? {
        return conn?.close()
    }

    override fun removeAndCloseConnection(): CompletableFuture<Void>? {
        val future = conn?.close()
        conn = null
        return future
    }

    fun addMsMessageHandler(handler: MsMessageHandler?) {
        handler?.let { msMessageHandlers.add(it) }
    }
}