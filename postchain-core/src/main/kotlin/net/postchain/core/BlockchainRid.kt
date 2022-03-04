package net.postchain.core

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex

/**
 * Wrapper type for a blockchain's external identifier (which is an array of bytes)
 *
 * Note 1: The internal indentifier is chainIid, which is just a sequence from the database.
 *
 * Note 2: This is a "core" class but the FACTORY for this class reside in "base" (since it uses encryption which is also in "base")
 */
data class BlockchainRid(val data: ByteArray) {

    init {
        if (data.size != 32) {
            throw IllegalArgumentException("Wrong size of Blockchain RID, was ${data.size} should be 32 (64 characters)")
        }
    }

    companion object {

        val ZERO_RID = BlockchainRid(ByteArray(32))

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
            "${take(2)}:${takeLast(3)}"
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