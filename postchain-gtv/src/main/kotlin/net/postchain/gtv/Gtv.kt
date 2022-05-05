// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.messages.RawGtv
import java.math.BigInteger

/**
 * Enum class of GTXML types
 * Note: order is same as in ASN.1, thus we can use same integer ids.
 */
enum class GtvType(val s: String) {
    NULL("null"), BYTEARRAY("bytea"), STRING("string"), INTEGER("int"), DICT("dict"), ARRAY("array"), BIGINTEGER("bigint");

    companion object {
        /**
         * Returns [GtvType] object correspondent to [String]
         */
        fun fromString(s: String): GtvType {
            return values().firstOrNull { it.s == s } ?: throw IllegalArgumentException("Unknown type of GtvType: $s")
        }
    }
}

/**
 * GTV stands for Generic Transfer Value, and is a (home made) format for data transfer.
 */
interface Gtv {
    val type: GtvType

    // Collection methods here
    operator fun get(index: Int): Gtv
    operator fun get(key: String): Gtv?

    // Convert to sub-class
    fun asString(): String
    fun asArray(): Array<out Gtv>
    fun isNull(): Boolean
    fun asDict(): Map<String, Gtv>
    fun asInteger(): Long
    fun asBigInteger(): BigInteger
    fun asBoolean(): Boolean
    fun asByteArray(convert: Boolean = false): ByteArray

    // Other conversions
    fun asPrimitive(): Any?
    fun getRawGtv(): RawGtv

    fun nrOfBytes(): Int
}

