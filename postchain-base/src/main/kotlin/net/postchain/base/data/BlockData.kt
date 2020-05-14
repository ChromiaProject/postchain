package net.postchain.base.data

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger

data class BlockData(val blockIid: Long, val blockRid: ByteArray, val chainIid: Long, val blockHeight: Long,
                     val blockHeader: ByteArray, val timestamp: Long) {

    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvInteger(blockIid), GtvByteArray(blockRid), GtvInteger(chainIid), GtvInteger(blockHeight),
                GtvByteArray(blockHeader), GtvInteger(timestamp))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): BlockData {
            return BlockData(
                    gtv[0].asInteger(),
                    gtv[1].asByteArray(),
                    gtv[2].asInteger(),
                    gtv[3].asInteger(),
                    gtv[4].asByteArray(),
                    gtv[5].asInteger()
            )
        }
    }
}