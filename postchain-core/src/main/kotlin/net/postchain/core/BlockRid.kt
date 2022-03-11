package net.postchain.core

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex

/**
 * Wrapper type for a BC block's identifier (which is an array of bytes)
 */
data class BlockRid(val data: ByteArray) {

    init {
        if (data.size != 32) {
            throw IllegalArgumentException("Wrong size of Block RID, was ${data.size} should be 32 (64 characters)")
        }
    }

    companion object {

        fun buildFromHex(str: String) = BlockchainRid(str.hexStringToByteArray())

        /**
         * For test, build a full length BC RID by repeating a single digit as a byte
         *
         * @param b is the byte to be repeated
         */
        fun buildRepeat(b: Byte): BlockchainRid {
            val bArr = ByteArray(32) { b }
            return BlockchainRid(bArr)
        }
    }

    fun toHex() = data.toHex()

    fun toShortHex(): String {
        return toHex().run {
            "${take(2)}:${takeLast(2)}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlockchainRid

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    override fun toString(): String {
        return toHex()
    }
}
