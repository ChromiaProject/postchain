package net.postchain.base.data

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger

data class TxData(val txIid: Long) {

    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvInteger(txIid))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): TxData {
            return TxData(
                    gtv[0].asInteger()
            )
        }
    }
}