package net.postchain.base.data

import net.postchain.gtv.*

data class BlockData(val blockIid: Long, val blockRid: ByteArray, val chainIid: Long, val blockHeight: Long,
                     val blockHeader: ByteArray, val witness: ByteArray, val timestamp: Long): BaseData() {

    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvInteger(blockIid), GtvByteArray(blockRid), GtvInteger(chainIid), GtvInteger(blockHeight),
                GtvByteArray(blockHeader), GtvByteArray(witness), GtvInteger(timestamp))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvInteger(blockIid), GtvByteArray(blockRid), GtvInteger(chainIid), GtvInteger(blockHeight),
                GtvByteArray(blockHeader), GtvInteger(timestamp))
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): BlockData {
            return BlockData(
                    gtv[0].asInteger(),
                    gtv[1].asByteArray(),
                    gtv[2].asInteger(),
                    gtv[3].asInteger(),
                    gtv[4].asByteArray(),
                    gtv[5].asByteArray(),
                    gtv[6].asInteger()
            )
        }
    }
}