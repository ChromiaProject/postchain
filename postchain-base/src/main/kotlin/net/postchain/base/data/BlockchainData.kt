package net.postchain.base.data

import net.postchain.gtv.*

data class BlockchainData(val chainIid: Long, val blockchainRid: ByteArray): BaseData() {

    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvInteger(chainIid), GtvByteArray(blockchainRid))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): BlockchainData {
            return BlockchainData(
                    gtv[0].asInteger(),
                    gtv[1].asByteArray()
            )
        }
    }
}