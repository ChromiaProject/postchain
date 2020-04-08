package net.postchain.base.data

import net.postchain.gtv.*

data class MetaData(val key: String, val value: String?) {
    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvString(key), GtvString(value.toString()))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): MetaData {
            return MetaData(
                    gtv[0].asString(),
                    gtv[1].asString()
            )
        }
    }
}