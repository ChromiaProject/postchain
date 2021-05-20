package net.postchain.network.masterslave.protocol

import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.network.masterslave.protocol.MsMessageType.*

object MsCodec {

    fun encode(message: MsMessage): ByteArray {
        val gtv = GtvFactory.gtv(
                GtvFactory.gtv(message.type.toLong()),
                GtvFactory.gtv(message.source),
                GtvFactory.gtv(message.destination),
                GtvFactory.gtv(message.blockchainRid),
                GtvFactory.gtv(message.payload)
        )

        return GtvEncoder.encodeGtv(gtv)
    }

    fun decode(bytes: ByteArray): MsMessage {
        val gtv = GtvDecoder.decodeGtv(bytes)

        val type = gtv[0].asInteger().toInt()
        val src = gtv[1].asByteArray()
        val dst = gtv[2].asByteArray()
        val brid = gtv[3].asByteArray()
        val data = gtv[4].asByteArray()

        if (type >= MsMessageType.values().size) {
            throw UnsupportedOperationException("Unknown MsMessage type: $type")
        }

        return when (MsMessageType.values()[type]) {
            HandshakeMessage -> MsHandshakeMessage(brid, data)
            DataMessage -> MsDataMessage(src, dst, brid, data)
            HeartbeatMessage -> MsHeartbeatMessage(brid, data)
        }
    }
}