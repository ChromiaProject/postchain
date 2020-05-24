package net.postchain.base.data

import net.postchain.gtv.*

data class TxData(val txIid: Long, val chainIid: Long, val txRid: ByteArray, val txData: ByteArray, val txHash: ByteArray, val blockIid: Long): BaseData() {

    override fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvInteger(txIid), GtvInteger(chainIid), GtvByteArray(txRid), GtvByteArray(txData), GtvByteArray(txHash), GtvInteger(blockIid))
    }

    override fun toHashGtv(): GtvArray {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray): TxData {
            return TxData(
                    gtv[0].asInteger(),
                    gtv[1].asInteger(),
                    gtv[2].asByteArray(),
                    gtv[3].asByteArray(),
                    gtv[4].asByteArray(),
                    gtv[5].asInteger()
            )
        }
    }
}