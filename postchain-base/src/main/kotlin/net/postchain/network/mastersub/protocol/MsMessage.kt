package net.postchain.network.mastersub.protocol

import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockQueries
import net.postchain.ebft.message.NullableGtv.gtvToNullableByteArray
import net.postchain.ebft.message.NullableGtv.nullableByteArrayToGtv
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.protocol.MsMessageType.BlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsMessageType.BlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsMessageType.CommittedBlock
import net.postchain.network.mastersub.protocol.MsMessageType.ConnectedPeers
import net.postchain.network.mastersub.protocol.MsMessageType.DataMessage
import net.postchain.network.mastersub.protocol.MsMessageType.HandshakeMessage
import net.postchain.network.mastersub.protocol.MsMessageType.QueryFailure
import net.postchain.network.mastersub.protocol.MsMessageType.QueryRequest
import net.postchain.network.mastersub.protocol.MsMessageType.QueryResponse

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

    fun getPayload(): Gtv
}

/**
 * MeMessage Types Enum class
 */
enum class MsMessageType {
    HandshakeMessage,
    DataMessage,
    ConnectedPeers,
    CommittedBlock,
    QueryRequest,
    QueryResponse,
    QueryFailure,
    BlockAtHeightRequest,
    BlockAtHeightResponse
}

/**
 * A handshake ms-message which is sent by a sub to master to establish connection.
 * For [MsHandshakeMessage] payload is a peers list for the master to establish connections with.
 */
class MsHandshakeMessage(
        val blockchainRid: ByteArray?,
        val peers: List<ByteArray>,
        val containerIID: Int
) : MsMessage {

    override val type = HandshakeMessage.ordinal

    constructor(payload: Gtv) :
            this(gtvToNullableByteArray(payload[0]), decodePeers(payload[1].asByteArray()), payload[2].asInteger().toInt())

    override fun getPayload(): Gtv {
        return gtv(nullableByteArrayToGtv(blockchainRid), gtv(encodePeers(peers)), gtv(containerIID.toLong()))
    }

}


/**
 * A data message which wraps the whole p2p-message.
 */
class MsDataMessage(
        val source: ByteArray, // A pubKey of [payload] sender peer
        val destination: ByteArray, // A pubKey of [payload] recipient peer
        val xPacket: ByteArray // Binary data of wrapped p2p-message
) : MsMessage {

    override val type = DataMessage.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asByteArray(),
            payload[1].asByteArray(),
            payload[2].asByteArray()
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(source),
                gtv(destination),
                gtv(xPacket)
        )
    }
}

/**
 * A list of connected peers for the given chain
 */
class MsConnectedPeersMessage(
        val blockchainRid: ByteArray,
        val connectedPeers: List<ByteArray>
) : MsMessage {
    override val type = ConnectedPeers.ordinal

    constructor(payload: Gtv) :
            this(payload[0].asByteArray(), decodePeers(payload[1].asByteArray()))

    override fun getPayload(): Gtv {
        return gtv(gtv(blockchainRid), gtv(encodePeers(connectedPeers)))
    }
}

/**
 * Subnode sends this to master after committing a block
 */
class MsCommittedBlockMessage(
        val blockchainRid: ByteArray,
        val blockHeight: Long
) : MsMessage {
    override val type = CommittedBlock.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asByteArray(),
            payload[1].asInteger()
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(blockchainRid),
                gtv(blockHeight)
        )
    }
}

/**
 * Make a query to a chain running on another (sub)node.
 */
class MsQueryRequest(
        val requestId: Long,

        /** `null` means to query chain0 using [DirectoryDataSource], otherwise use [BlockQueries] to query specific chain. */
        val targetBlockchainRid: BlockchainRid?,

        val name: String,
        val args: Gtv
) : MsMessage {
    override val type = QueryRequest.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asInteger(),
            if (payload[1].isNull()) null else BlockchainRid(payload[1].asByteArray()),
            payload[2].asString(),
            payload[3],
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(requestId),
                if (targetBlockchainRid != null) gtv(targetBlockchainRid) else GtvNull,
                gtv(name),
                args,
        )
    }
}

/**
 * Successful response to [MsQueryRequest].
 */
class MsQueryResponse(
        val requestId: Long,
        val result: Gtv
) : MsMessage {
    override val type = QueryResponse.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asInteger(),
            payload[1],
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(requestId),
                result,
        )
    }
}

/**
 * Request a block from a chain running on another (sub)node.
 */
class MsBlockAtHeightRequest(
        val requestId: Long,
        val targetBlockchainRid: BlockchainRid,
        val height: Long
) : MsMessage {
    override val type = BlockAtHeightRequest.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asInteger(),
            BlockchainRid(payload[1].asByteArray()),
            payload[2].asInteger()
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(requestId),
                gtv(targetBlockchainRid),
                gtv(height)
        )
    }
}

/**
 * Successful response to [MsBlockAtHeightRequest].
 */
class MsBlockAtHeightResponse(
        val requestId: Long,
        /** `null` means block not found. */
        val block: BlockDetail?
) : MsMessage {
    override val type = BlockAtHeightResponse.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asInteger(),
            if (payload[1].isNull()) null else GtvObjectMapper.fromGtv(payload[1], BlockDetail::class),
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(requestId),
                if (block == null) GtvNull else GtvObjectMapper.toGtvDictionary(block),
        )
    }
}

/**
 * Unsuccessful response to [MsQueryRequest] or [MsBlockAtHeightRequest].
 */
class MsQueryFailure(
        val requestId: Long,
        val errorMessage: String
) : MsMessage {
    override val type = QueryFailure.ordinal

    constructor(payload: Gtv) : this(
            payload[0].asInteger(),
            payload[1].asString(),
    )

    override fun getPayload(): Gtv {
        return gtv(
                gtv(requestId),
                gtv(errorMessage),
        )
    }
}

// ----------------
// Utility functions
// ----------------

fun encodePeers(singers: List<ByteArray>): ByteArray {
    val gtv = gtv(singers.map { gtv(it) })
    return GtvEncoder.encodeGtv(gtv)
}

fun decodePeers(bytes: ByteArray): List<ByteArray> =
        GtvDecoder.decodeGtv(bytes).asArray().map { it.asByteArray() }

