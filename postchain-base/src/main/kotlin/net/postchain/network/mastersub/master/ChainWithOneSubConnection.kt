package net.postchain.network.mastersub.master

import net.postchain.network.common.ChainWithOneConnection
import net.postchain.network.mastersub.MsMessageHandler
import java.util.concurrent.CompletableFuture

/**
 *
 */
class ChainWithOneSubConnection(val config: SubChainConfig) :
    ChainWithOneConnection<MasterConnection, MsMessageHandler> {

    // Maximum ONE connection
    var conn: MasterConnection? = null
    var handshakeReceived = false

    // ---------------------------
    // Trivial impl of the interface
    // ----------------------------
    override fun getChainIid() = config.chainId
    override fun getBlockchainRid() = config.blockchainRid
    override fun getPacketHandler() = config.messageHandler
    override fun isConnected() = conn != null
    override fun getConnection() = conn
    override fun setConnection(newConn: MasterConnection) {
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
}