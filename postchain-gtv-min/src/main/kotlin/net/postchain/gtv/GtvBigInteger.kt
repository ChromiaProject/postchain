package net.postchain.gtv

import java.math.BigInteger

public data class GtvBigInteger(val integer: BigInteger) : GtvPrimitive() {
    override val type: GtvType
        get() = GtvType.BIGINTEGER

    override fun asBigInteger(): BigInteger = integer
    override fun asPrimitive(): Any = integer

    override fun nrOfBytes(): Int = ((integer.bitLength() + 1) / 8) + 1

    override fun shortString(): String = if (nrOfBytes() > 16) "very_big_integer" else toString()

    override fun toString(): String = integer.toString() + "L"
}
