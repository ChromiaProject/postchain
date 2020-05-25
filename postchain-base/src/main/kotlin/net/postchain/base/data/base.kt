package net.postchain.base.data

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray

interface FromGtv {
    fun fromGtv(gtv: GtvArray<Gtv>): BaseData?
}

abstract class BaseData {
    abstract fun toGtv(): GtvArray<Gtv>
    abstract fun toHashGtv(): GtvArray<Gtv>
}