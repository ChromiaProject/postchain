package net.postchain.base.data

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger

data class ConfigurationData(val chainIid: Long, val height: Long, val data: ByteArray) {
    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvInteger(chainIid), GtvInteger(height), GtvByteArray(data))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): ConfigurationData {
            return ConfigurationData(
                    gtv[0].asInteger(),
                    gtv[1].asInteger(),
                    gtv[2].asByteArray()
            )
        }
    }
}