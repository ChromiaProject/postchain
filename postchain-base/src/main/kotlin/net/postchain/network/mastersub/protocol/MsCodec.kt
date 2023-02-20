package net.postchain.network.mastersub.protocol

import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.network.mastersub.protocol.MsMessageType.*

object MsCodec {

    fun encode(message: MsMessage): ByteArray {
        val gtv = GtvFactory.gtv(
                GtvFactory.gtv(message.type.toLong()),
                GtvFactory.gtv(message.blockchainRid),
                message.getPayload()
        )

        return GtvEncoder.encodeGtv(gtv)
    }

    fun decode(bytes: ByteArray): MsMessage {
        val gtv = GtvDecoder.decodeGtv(bytes)
        val type = gtv[0].asInteger().toInt()
        val brid = gtv[1].asByteArray()
        val payload = gtv[2]

        if (type >= MsMessageType.values().size) {
            throw UnsupportedOperationException("Unknown MsMessage type: $type")
        }

        return when (MsMessageType.values()[type]) {
            HandshakeMessage -> MsHandshakeMessage(brid, payload)
            DataMessage -> MsDataMessage(brid, payload)
            FindNextBlockchainConfig -> MsFindNextBlockchainConfigMessage(brid, payload)
            NextBlockchainConfig -> MsNextBlockchainConfigMessage(brid, payload)
            ConnectedPeers -> MsConnectedPeersMessage(brid, payload)
            CommittedBlock -> MsCommittedBlockMessage(brid, payload)
            QueryRequest -> MsQueryRequest(brid, payload)
            QueryResponse -> MsQueryResponse(brid, payload)
            QueryFailure -> MsQueryFailure(brid, payload)
            BlockAtHeightRequest -> MsBlockAtHeightRequest(brid, payload)
            BlockAtHeightResponse -> MsBlockAtHeightResponse(brid, payload)
        }
    }
}