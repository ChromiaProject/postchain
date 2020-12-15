package net.postchain.network.masterslave.protocol

import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory

/**
 * MsMessages are used in master-slave communication to let slave nodes
 * communicate with the p2p-network through the master node.
 *
 * For [MsHandshakeMessage] payload is a peers list for the master to establish connections with.
 * For [MsDataMessage] payload is the whole p2p-message.
 */
interface MsMessage {

    /**
     * Type of message: HandshakeMsMessage (0), DataMsMessage (1)
     */
    val type: Int

    /**
     * A pubKey of [payload] sender peer
     */
    val source: ByteArray

    /**
     * A pubKey of [payload] recipient peer
     */
    val destination: ByteArray

    /**
     * BlockchainRid of [payload]
     */
    val blockchainRid: ByteArray

    /**
     * Binary data of wrapped p2p-message
     */
    val payload: ByteArray
}


/**
 * A handshake ms-message which is sent by a slave to master to establish connection.
 * For [MsHandshakeMessage] payload is a peers list for the master to establish connections with.
 */
class MsHandshakeMessage(
        override val blockchainRid: ByteArray,
        override val payload: ByteArray
) : MsMessage {
    override val type = 0
    override val source: ByteArray = byteArrayOf()
    override val destination: ByteArray = byteArrayOf()
    val peers: List<ByteArray> = decodePeers(payload)

    constructor(blockchainRid: ByteArray, peers: List<ByteArray>)
            : this(blockchainRid, encodePeers(peers))

    companion object {

        fun decodePeers(bytes: ByteArray): List<ByteArray> =
                GtvDecoder.decodeGtv(bytes).asArray().map { it.asByteArray() }

        fun encodePeers(singers: List<ByteArray>): ByteArray {
            val gtv = singers.map { GtvFactory.gtv(it) }.let { GtvFactory.gtv(it) }
            return GtvEncoder.encodeGtv(gtv)
        }
    }
}


/**
 * A data message which wraps the whole p2p-message.
 */
class MsDataMessage(
        override val source: ByteArray,
        override val destination: ByteArray,
        override val blockchainRid: ByteArray,
        override val payload: ByteArray
) : MsMessage {
    override val type = 1
}