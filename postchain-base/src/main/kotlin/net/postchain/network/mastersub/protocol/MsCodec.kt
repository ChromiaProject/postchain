package net.postchain.network.mastersub.protocol

import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.network.mastersub.protocol.MsMessageType.*

object MsCodec {

    fun encode(message: MsMessage): ByteArray {
        val gtv = GtvFactory.gtv(
                GtvFactory.gtv(message.type.toLong()),
                message.getPayload()
        )

        return GtvEncoder.encodeGtv(gtv)
    }

    fun decode(bytes: ByteArray): MsMessage {
        val gtv = GtvDecoder.decodeGtv(bytes)
        val type = gtv[0].asInteger().toInt()
        val payload = gtv[1]

        if (type >= MsMessageType.values().size) {
            throw UnsupportedOperationException("Unknown MsMessage type: $type")
        }

        return when (MsMessageType.values()[type]) {
            HandshakeMessage -> MsHandshakeMessage(payload)
            DataMessage -> MsDataMessage(payload)
            ConnectedPeers -> MsConnectedPeersMessage(payload)
            CommittedBlock -> MsCommittedBlockMessage(payload)
            QueryRequest -> MsQueryRequest(payload)
            QueryResponse -> MsQueryResponse(payload)
            QueryFailure -> MsQueryFailure(payload)
            BlockAtHeightRequest -> MsBlockAtHeightRequest(payload)
            BlockAtHeightResponse -> MsBlockAtHeightResponse(payload)
        }
    }
}