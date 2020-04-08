package net.postchain.base.data

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger

data class BlockchainData(val chainIid: Long, val blockchainRid: ByteArray) {
    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvInteger(chainIid), GtvByteArray(blockchainRid))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): BlockchainData {
            return BlockchainData(
                    gtv[0].asInteger(),
                    gtv[1].asByteArray()
            )
        }
    }
}