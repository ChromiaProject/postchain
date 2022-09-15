package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

data class SentIcmfMessage(
    val topic: String, // Topic of message
    val body: Gtv,
    val previousMessageBlockHeight: Long
) {

    companion object {
        fun fromGtv(gtv: Gtv): SentIcmfMessage =
            SentIcmfMessage(gtv["topic"]!!.asString(), gtv["body"]!!, gtv["previous_message_block_height"]!!.asInteger())
    }
}
