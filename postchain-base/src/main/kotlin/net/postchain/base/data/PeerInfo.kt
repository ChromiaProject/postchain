package net.postchain.base.data

import net.postchain.gtv.*

data class PeerInfo(val host: String, val port: Int, val pubkey: String, val timestamp: Long): BaseData() {
    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvString(host), GtvInteger(port.toLong()), GtvString(pubkey), GtvInteger(timestamp))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): PeerInfo {
            return PeerInfo(
                    gtv[0].asString(),
                    gtv[1].asInteger().toInt(),
                    gtv[2].asString(),
                    gtv[3].asInteger()
            )
        }
    }
}