package net.postchain.base.data

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger

data class BlockData(val blockIid: Long, val blockHeader: ByteArray, val witness: ByteArray) {

    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvInteger(blockIid), GtvByteArray(blockHeader), GtvByteArray(witness))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): BlockData {
            return BlockData(
                    gtv[0].asInteger(),
                    gtv[1].asByteArray(),
                    gtv[2].asByteArray()
            )
        }
    }
}