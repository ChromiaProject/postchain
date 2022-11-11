package net.postchain.network.mastersub.protocol

import net.postchain.common.data.Hash
import net.postchain.gtv.*
import net.postchain.network.mastersub.protocol.MsMessageType.*

// TODO: [POS-164]: Fix kdoc

/**
 * MsMessages are used in master-sub communication to let subnodes
 * communicate with the p2p-network through the master node.
 *
 * TODO: [POS-164]: Fix kdoc
 * For [MsHandshakeMessage] payload is a peers list for the master to establish connections with.
 * For [MsDataMessage] payload is the whole p2p-message.
 */
interface MsMessage {

    val type: Int
    val blockchainRid: ByteArray

    fun getPayload(): Gtv
}

private fun gtvToNullableLong(gtv: Gtv): Long? = if (gtv.isNull()) null else gtv.asInteger()
private fun nullableLongToGtv(value: Long?): Gtv = if (value == null) GtvNull else GtvFactory.gtv(value)
private fun gtvToNullableByteArray(gtv: Gtv): ByteArray? = if (gtv.isNull()) null else gtv.asByteArray()
private fun nullableByteArrayToGtv(value: ByteArray?): Gtv = if (value == null) GtvNull else GtvFactory.gtv(value)

/**
 * MeMessage Types Enum class
 */
enum class MsMessageType {
    HandshakeMessage,
    DataMessage,
    FindNextBlockchainConfig,
    NextBlockchainConfig,
    ConnectedPeers
}

/**
 * A handshake ms-message which is sent by a sub to master to establish connection.
 * For [MsHandshakeMessage] payload is a peers list for the master to establish connections with.
 */
class MsHandshakeMessage(
        override val blockchainRid: ByteArray,
        val peers: List<ByteArray>
) : MsMessage {

    override val type = HandshakeMessage.ordinal

    constructor(blockchainRid: ByteArray, payload: Gtv) :
            this(blockchainRid, decodePeers(payload.asByteArray()))

    override fun getPayload(): Gtv {
        return GtvFactory.gtv(encodePeers(peers))
    }

}


/**
 * A data message which wraps the whole p2p-message.
 */
class MsDataMessage(
        override val blockchainRid: ByteArray,
        val source: ByteArray, // A pubKey of [payload] sender peer
        val destination: ByteArray, // A pubKey of [payload] recipient peer
        val xPacket: ByteArray // Binary data of wrapped p2p-message
) : MsMessage {

    override val type = DataMessage.ordinal

    constructor(blockchainRid: ByteArray, payload: Gtv) : this(
            blockchainRid,
            payload[0].asByteArray(),
            payload[1].asByteArray(),
            payload[2].asByteArray()
    )

    override fun getPayload(): Gtv {
        return GtvFactory.gtv(
                GtvFactory.gtv(source),
                GtvFactory.gtv(destination),
                GtvFactory.gtv(xPacket)
        )
    }
}

/**
 * A GetBlockchainConfig message which wraps the whole p2p-message.
 */
class MsFindNextBlockchainConfigMessage(
        override val blockchainRid: ByteArray,
        val currentHeight: Long,
        val nextHeight: Long?
) : MsMessage {
    override val type = FindNextBlockchainConfig.ordinal

    constructor(blockchainRid: ByteArray, payload: Gtv) : this(
            blockchainRid,
            payload[0].asInteger(),
            gtvToNullableLong(payload[1])
    )

    override fun getPayload(): Gtv {
        return GtvFactory.gtv(
                GtvFactory.gtv(currentHeight),
                nullableLongToGtv(nextHeight)
        )
    }
}

/**
 * A BlockchainConfig message which wraps the whole p2p-message.
 */
class MsNextBlockchainConfigMessage(
        override val blockchainRid: ByteArray,
        val nextHeight: Long?,
        val rawConfig: ByteArray?,
        val configHash: Hash?
) : MsMessage {
    override val type = NextBlockchainConfig.ordinal

    constructor(blockchainRid: ByteArray, payload: Gtv) : this(
            blockchainRid,
            gtvToNullableLong(payload[0]),
            gtvToNullableByteArray(payload[1]),
            gtvToNullableByteArray(payload[2])
    )

    override fun getPayload(): Gtv {
        return GtvFactory.gtv(
                nullableLongToGtv(nextHeight),
                nullableByteArrayToGtv(rawConfig),
                nullableByteArrayToGtv(configHash))
    }
}

/**
 * A list of connected peers for the given chain
 */
class MsConnectedPeersMessage(
        override val blockchainRid: ByteArray,
        val connectedPeers: List<ByteArray>
) : MsMessage {
    override val type = ConnectedPeers.ordinal

    constructor(blockchainRid: ByteArray, payload: Gtv) :
            this(blockchainRid, decodePeers(payload.asByteArray()))

    override fun getPayload(): Gtv {
        return GtvFactory.gtv(encodePeers(connectedPeers))
    }
}

// ----------------
// Utility functions
// ----------------

fun encodePeers(singers: List<ByteArray>): ByteArray {
    val gtv = singers.map { GtvFactory.gtv(it) }.let { GtvFactory.gtv(it) }
    return GtvEncoder.encodeGtv(gtv)
}

fun decodePeers(bytes: ByteArray): List<ByteArray> =
        GtvDecoder.decodeGtv(bytes).asArray().map { it.asByteArray() }

