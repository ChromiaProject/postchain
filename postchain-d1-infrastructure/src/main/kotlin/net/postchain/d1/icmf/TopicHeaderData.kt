package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

data class TopicHeaderData(val hash: ByteArray, val prevMessageBlockHeight: Long) {
    companion object {
        fun fromGtv(gtv: Gtv): TopicHeaderData = TopicHeaderData(gtv["hash"]!!.asByteArray(), gtv["prev_message_block_height"]!!.asInteger())
    }

    fun toGtv(): Gtv = gtv("hash" to gtv(hash), "prev_message_block_height" to gtv(prevMessageBlockHeight))
}
