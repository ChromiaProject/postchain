package net.postchain.gtv

import java.math.BigInteger

/**
 * Just a base class for all GTVs.
 */
public abstract class AbstractGtv : Gtv {

    override fun get(index: Int): Gtv = throw GtvTypeException(errorMessage("array"))

    override fun get(key: String): Gtv? = throw GtvTypeException(errorMessage("dict"))

    override fun asString(): String = throw GtvTypeException(errorMessage("string"))

    override fun asArray(): Array<out Gtv> = throw GtvTypeException(errorMessage("array"))

    override fun isNull(): Boolean = false

    override fun asDict(): Map<String, Gtv> = throw GtvTypeException(errorMessage("dict"))

    override fun asInteger(): Long = throw GtvTypeException(errorMessage("integer"))

    override fun asBigInteger(): BigInteger = throw GtvTypeException(errorMessage("big integer"))

    override fun asBoolean(): Boolean = throw GtvTypeException(errorMessage("boolean"))

    override fun asByteArray(convert: Boolean): ByteArray = throw GtvTypeException(errorMessage("byte array"))

    override fun nrOfBytes(): Int = throw GtvTypeException("Implementation expected")

    private fun errorMessage(expectedType: String) =
        "Type error: $expectedType expected, found $type with value ${toString()}"
}
