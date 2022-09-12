package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

data class SentIcmfMessage(
    val topic: String, // Topic of message
    val body: Gtv
) {

    companion object {
        fun fromGtv(gtv: Gtv): SentIcmfMessage = SentIcmfMessage(gtv["topic"]!!.asString(), gtv["body"]!!)
    }

    fun toGtv(): Gtv = gtv("topic" to gtv(topic), "body" to body)
}
