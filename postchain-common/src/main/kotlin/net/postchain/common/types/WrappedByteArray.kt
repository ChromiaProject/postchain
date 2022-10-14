package net.postchain.common.types

import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.toHex

/**
 * Wrapped [ByteArray] [equals] and [hashCode] implemented using [java.util.Arrays.equals] and [java.util.Arrays.hashCode].
 *
 * Wraps the ByteArray with contentEqualsTo
 * @param data the [ByteArray] to be wrapped
 */
class WrappedByteArray(val data: ByteArray) {
    val size get() = data.size

    /**
     * [ByteArray]
     */
    constructor(size: Int, init: (Int) -> Byte) : this(ByteArray(size, init))

    /**
     * [ByteArray]
     */
    constructor(size: Int) : this(ByteArray(size))

    /**
     * [ByteArray.get]
     */
    operator fun get(index: Int) = data[index]

    /**
     * [ByteArray.set]
     */
    operator fun set(index: Int, value: Byte) = data.set(index, value)

    /**
     * [ByteArray.iterator]
     */
    operator fun iterator() = data.iterator()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappedByteArray

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode() = data.contentHashCode()


    override fun toString() = data.contentToString()

    fun toHex() = data.toHex()

    companion object {
        @JvmStatic
        fun fromHex(hex: String) = hex.hexStringToWrappedByteArray()
    }
}
