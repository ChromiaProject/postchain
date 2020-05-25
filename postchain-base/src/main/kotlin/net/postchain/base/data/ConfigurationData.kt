package net.postchain.base.data

import net.postchain.gtv.*

data class ConfigurationData(val chainIid: Long, val height: Long, val data: ByteArray): BaseData() {

    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvInteger(chainIid), GtvInteger(height), GtvByteArray(data))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): ConfigurationData {
            return ConfigurationData(
                    gtv[0].asInteger(),
                    gtv[1].asInteger(),
                    gtv[2].asByteArray()
            )
        }
    }
}