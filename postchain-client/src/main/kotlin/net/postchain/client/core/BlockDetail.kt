package net.postchain.client.core

import com.google.gson.annotations.JsonAdapter
import net.postchain.gtv.Gtv

class BlockDetail(
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val rid: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val prevBlockRID: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val header: ByteArray,
        val height: Long,
        val transactions: List<TxDetail>,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val witness: ByteArray,
        val timestamp: Long
) {
        companion object {
                fun fromGtv(gtv: Gtv): BlockDetail? {
                        if (gtv.isNull()) return null
                        return BlockDetail(
                                gtv["rid"]!!.asByteArray(),
                                gtv["prevBlockRID"]!!.asByteArray(),
                                gtv["header"]!!.asByteArray(),
                                gtv["height"]!!.asInteger(),
                                gtv["transactions"]!!.asArray().map { TxDetail.fromGtv(it) },
                                gtv["witness"]!!.asByteArray(),
                                gtv["timestamp"]!!.asInteger()
                        )
                }
        }
}
