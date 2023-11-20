package net.postchain.network.common

import net.postchain.common.BlockchainRid
import java.util.concurrent.CompletableFuture

/**
 * This is a mapping between a blockchain and its connection to another node.
 */
interface ChainWithOneConnection<NodeConnectionType, PacketHandler> {

    /**
     * @return the chain IID
     */
    fun getChainIid(): Long

    /**
     * @return the BC RID or throw
     */
    fun getBlockchainRid(): BlockchainRid

    fun getPacketHandler(): PacketHandler?

    // ----------
    // Connections
    // This type has an internal collection of connections
    // ----------
    fun isConnected(): Boolean
    fun getConnection(): NodeConnectionType?
    fun setConnection(newConn: NodeConnectionType)

    /**
     * Will close the connection
     */
    fun closeConnection(): CompletableFuture<Void>?

    /**
     * Remove and class conn
     */
    fun removeAndCloseConnection(): CompletableFuture<Void>?
}