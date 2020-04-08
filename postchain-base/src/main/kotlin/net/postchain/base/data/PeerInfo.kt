package net.postchain.base.data

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString

data class PeerInfo(val host: String, val port: Int, val pubkey: String, val timestamp: Long) {
    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvString(host), GtvInteger(port.toLong()), GtvString(pubkey), GtvInteger(timestamp))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): PeerInfo {
            return PeerInfo(
                    gtv[0].asString(),
                    gtv[1].asInteger().toInt(),
                    gtv[2].asString(),
                    gtv[3].asInteger()
            )
        }
    }
}