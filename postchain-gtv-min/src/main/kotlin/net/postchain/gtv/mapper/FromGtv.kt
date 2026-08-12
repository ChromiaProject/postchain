package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv

public fun interface FromGtv<T> {
    public fun fromGtv(gtv: Gtv): T
}
