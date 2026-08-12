package net.postchain.gtv

import net.postchain.common.toHex

public data class GtvByteArray(val bytearray: ByteArray) : GtvPrimitive() {
    override val type: GtvType
        get() = GtvType.BYTEARRAY

    override fun asByteArray(convert: Boolean): ByteArray = bytearray
    override fun asPrimitive(): Any = bytearray
    override fun nrOfBytes(): Int = bytearray.size

    override fun equals(other: Any?): Boolean =
        this === other || (other is GtvByteArray && bytearray.contentEquals(other.bytearray))

    override fun hashCode(): Int = 31 * bytearray.contentHashCode() + type.hashCode()
    override fun shortString(): String = if (nrOfBytes() > 33) "long_byte_array" else toString()
    override fun toString(): String = "x\"${bytearray.toHex()}\""
}
