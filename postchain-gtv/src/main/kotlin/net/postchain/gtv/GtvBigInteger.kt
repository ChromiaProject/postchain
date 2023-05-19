// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.beanit.jasn1.ber.types.BerInteger
import net.postchain.gtv.gtvmessages.RawGtv
import java.math.BigInteger

data class GtvBigInteger(val integer: BigInteger) : GtvPrimitive() {

    override val type: GtvType = GtvType.BIGINTEGER

    override fun asBigInteger(): BigInteger {
        return integer
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv().apply { bigInteger = BerInteger(this@GtvBigInteger.integer) }
    }

    override fun asPrimitive(): Any {
        return integer
    }

    override fun nrOfBytes(): Int {
        return ((integer.bitLength() + 1) / 8) + 1
    }

    override fun toString(): String {
        return integer.toString() + "L"
    }
}
