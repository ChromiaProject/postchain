package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv

fun interface ToGtv {
    fun toGtv(): Gtv
}
