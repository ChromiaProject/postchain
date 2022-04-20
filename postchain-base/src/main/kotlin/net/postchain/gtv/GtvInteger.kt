// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.messages.RawGtv
import org.openmuc.jasn1.ber.types.BerInteger
import java.math.BigInteger

data class GtvInteger(val integer: Long) : GtvPrimitive() {

    override val type: GtvType = GtvType.INTEGER

    override fun asInteger(): Long {
        return integer
    }

    override fun asBigInteger(): BigInteger {
        return integer.toBigInteger()
    }

    override fun asBoolean(): Boolean {
        return integer.toBoolean()
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv(null, null, null, BerInteger(integer), null, null)
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
