package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.network.common.ChainWithOneConnection
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsDataMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.peer.XChainPeersConfiguration


/**
 * A chain running on a sub node only has one connection: to the master.
 */
class ChainWithOneMasterConnection(
    val config: XChainPeersConfiguration,
    msMessageHandlerSupplier: (Long) -> MsMessageHandler?
) : ChainWithOneConnection<SubConnection, MsMessageHandler> {

    companion object : KLogging()

    val peers: List<ByteArray> = (config.commConfiguration as SubPeerCommConfig).peers

    val msMessageHandler: MsMessageHandler = object : MsMessageHandler {

        /**
         * If the [MsMessage] is a wrapped EBFT message [MsDataMessage], when we use the
         */
        override fun onMessage(message: MsMessage) {
            logger.debug { "onMessage() - Begin: Message type: ${message.type} " }
            when (message) {
                is MsDataMessage -> config.peerPacketHandler.handle(message.xPacket, NodeRid(message.source))
                else -> msMessageHandlerSupplier(config.chainId)?.onMessage(message)
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
    override fun getPacketHandler() = msMessageHandler
    override fun isConnected() = conn != null
    override fun getConnection() = conn
    override fun setConnection(newConn: SubConnection) {
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