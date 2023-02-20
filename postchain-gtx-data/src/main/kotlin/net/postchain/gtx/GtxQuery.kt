package net.postchain.gtx

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv

data class GtxQuery(val name: String, val args: Gtv) {

    fun encode() = GtvEncoder.encodeGtv(gtv(gtv(name), args))

    companion object {
        @JvmStatic
        fun decode(b: ByteArray): GtxQuery {
            val gtv = GtvDecoder.decodeGtv(b)
            if (gtv !is GtvArray || gtv.asArray().size != 2) throw IllegalArgumentException("Gtx Query must be an array with 2 elements")
            return GtxQuery(gtv[0].asString(), gtv[1])
        }
    }
}
