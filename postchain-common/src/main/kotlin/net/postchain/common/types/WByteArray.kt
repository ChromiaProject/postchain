package net.postchain.common.types

/**
 * Wrapped [ByteArray]
 *
 * Wraps the ByteArray with contentEqualsTo
 * @param data the [ByteArray] to be wrapped
 */
class WByteArray(val data: ByteArray) {
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

        other as WByteArray

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}
