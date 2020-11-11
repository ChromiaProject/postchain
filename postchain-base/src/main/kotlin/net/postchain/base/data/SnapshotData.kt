package net.postchain.base.data

import net.postchain.gtv.*

data class SnapshotData(val chainIid: Long, val snapshotIid: Long, val txIid: Long, val blockHeight: Long,
                        val snapshotHash: String, val pubkey: String): BaseData() {

    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvInteger(chainIid), GtvInteger(snapshotIid), GtvInteger(txIid), GtvInteger(blockHeight),
                GtvString(snapshotHash), GtvString(pubkey))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): SnapshotData {
            return SnapshotData(
                    gtv[0].asInteger(),
                    gtv[1].asInteger(),
                    gtv[2].asInteger(),
                    gtv[3].asInteger(),
                    gtv[4].asString(),
                    gtv[5].asString()
            )
        }
    }
}