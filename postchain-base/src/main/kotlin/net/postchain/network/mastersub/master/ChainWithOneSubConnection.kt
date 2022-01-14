package net.postchain.network.mastersub.master

import net.postchain.network.common.ChainWithOneConnection
import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler

/**
 *
 */
class ChainWithOneSubConnection (val config: SubChainConfig):
    ChainWithOneConnection<NodeConnection<MsMessageHandler, MasterConnectionDescriptor>, MsMessageHandler>  // Type magic
{

    // Maximum ONE connection
    var conn: NodeConnection<MsMessageHandler, MasterConnectionDescriptor>? = null

    // ---------------------------
    // Trivial impl of the interface
    // ----------------------------
    override fun getChainIid() = config.chainId
    override fun getBlockchainRid() = config.blockchainRid
    override fun getPacketHandler() = config.messageHandler
    override fun isConnected() = conn != null
    override fun getConnection() = conn
    override fun setConnection(newConn: NodeConnection<MsMessageHandler, MasterConnectionDescriptor>) {
        conn = newConn
    }

    override fun closeConnection() {
        conn?.close()
    }

    override fun removeAndCloseConnection() {
        conn?.close()
        conn = null
    }
}