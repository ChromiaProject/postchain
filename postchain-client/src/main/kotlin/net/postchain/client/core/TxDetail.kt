package net.postchain.client.core

import com.google.gson.annotations.JsonAdapter
import net.postchain.gtv.Gtv

class TxDetail(
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val rid: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val hash: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val data: ByteArray?
) {
        companion object {
                fun fromGtv(gtv: Gtv): TxDetail {
                        val dataGtv = gtv["data"]!!
                        val data = if (dataGtv.isNull()) {
                                null
                        } else {
                                dataGtv.asByteArray()
                        }
                        return TxDetail(
                                gtv["rid"]!!.asByteArray(),
                                gtv["hash"]!!.asByteArray(),
                                data
                        )
                }
        }
}
