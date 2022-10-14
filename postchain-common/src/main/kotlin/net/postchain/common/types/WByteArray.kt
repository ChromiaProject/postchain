package net.postchain.common.types

/**
 * Wrapped ByteArray
 *
 * Wraps the ByteArray with contentEqualsTo
 * @param data the [ByteArray] to be wrapped
 */
class WByteArray(val data: ByteArray) {
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
