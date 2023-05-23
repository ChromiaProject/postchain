// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.beanit.jasn1.ber.types.BerInteger
import net.postchain.gtv.gtvmessages.RawGtv

data class GtvInteger(val integer: Long) : GtvPrimitive() {

    override val type: GtvType = GtvType.INTEGER

    override fun asInteger(): Long {
        return integer
    }

    override fun asBoolean(): Boolean {
        return integer != 0L
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv().apply { integer = BerInteger(this@GtvInteger.integer) }
    }

    override fun asPrimitive(): Any {
        return integer
    }

    override fun nrOfBytes(): Int {
        return Long.SIZE_BYTES
    }

    override fun toString(): String {
        return integer.toString()
    }
}
