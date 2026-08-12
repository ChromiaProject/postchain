package net.postchain.gtv

import java.math.BigInteger

/**
 * The GTV types.
 *
 * Note: order is the same as in the ASN.1 schema, so the ordinals double as the choice tags.
 */
public enum class GtvType(public val s: String) {
    NULL("null"),
    BYTEARRAY("bytea"),
    STRING("string"),
    INTEGER("int"),
    DICT("dict"),
    ARRAY("array"),
    BIGINTEGER("bigint");

    public companion object {
        /** Looks up a type by its textual name, as used in GtvML and in `<param type="...">`. */
        public fun fromString(s: String): GtvType =
            requireNotNull(entries.firstOrNull { it.s == s }) { "Unknown type of GtvType: $s" }
    }
}

/**
 * GTV stands for Generic Transfer Value, and is a (home made) format for data transfer.
 */
public interface Gtv {
    public val type: GtvType

    // Collection methods here
    public operator fun get(index: Int): Gtv
    public operator fun get(key: String): Gtv?

    // Convert to sub-class
    public fun asString(): String
    public fun asArray(): Array<out Gtv>
    public fun isNull(): Boolean
    public fun asDict(): Map<String, Gtv>
    public fun asInteger(): Long
    public fun asBigInteger(): BigInteger
    public fun asBoolean(): Boolean
    public fun asByteArray(convert: Boolean = false): ByteArray

    // Other conversions
    public fun asPrimitive(): Any?

    public fun nrOfBytes(): Int

    /**
     * @return a human-readable string representation, truncated if the value is large.
     */
    public fun shortString(): String
}
