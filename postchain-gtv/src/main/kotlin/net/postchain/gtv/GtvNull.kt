// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.beanit.jasn1.ber.types.BerNull
import net.postchain.gtv.messages.RawGtv

object GtvNull : GtvPrimitive() {

    override val type: GtvType = GtvType.NULL

    override fun isNull(): Boolean {
        return true
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv().apply { null_ = BerNull() }
    }

    override fun asPrimitive(): Any? {
        return null
    }

    override fun nrOfBytes(): Int {
        return 0
    }
}