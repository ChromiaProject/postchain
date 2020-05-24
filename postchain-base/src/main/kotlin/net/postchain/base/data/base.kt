package net.postchain.base.data

import net.postchain.gtv.GtvArray

interface FromGtv {
    fun fromGtv(gtv: GtvArray): BaseData?
}

abstract class BaseData {
    abstract fun toGtv(): GtvArray
    abstract fun toHashGtv(): GtvArray
}