package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv

fun interface FromGtv<T> {
    fun fromGtv(gtv: Gtv): T
}
