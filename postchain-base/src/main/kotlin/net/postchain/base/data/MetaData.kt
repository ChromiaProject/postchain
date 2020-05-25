package net.postchain.base.data

import net.postchain.gtv.*

data class MetaData(val key: String, val value: String?): BaseData() {

    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvString(key), GtvString(value.toString()))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): MetaData {
            return MetaData(
                    gtv[0].asString(),
                    gtv[1].asString()
            )
        }
    }
}