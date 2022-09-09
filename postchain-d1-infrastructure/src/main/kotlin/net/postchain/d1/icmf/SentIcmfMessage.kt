package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

data class SentIcmfMessage(
    val topic: String, // Topic of message
    val body: Gtv
) {

    companion object {
        fun fromGtv(gtv: Gtv): SentIcmfMessage {
            return SentIcmfMessage(gtv["topic"]!!.asString(), gtv["body"]!!)
        }
    }

    fun toGtv(): Gtv {
        return gtv("topic" to gtv(topic), "body" to body)
    }
}
