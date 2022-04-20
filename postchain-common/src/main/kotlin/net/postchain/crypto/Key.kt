package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.spongycastle.util.Arrays

class Key(val byteArray: ByteArray): Comparable<Key> {
    val asString get() = byteArray.toHex()

    override fun toString() = "Key($asString)"

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (super.equals(other)) return true
        return (other as? Key)?.byteArray?.contentEquals(byteArray) ?: false
    }

    override fun hashCode(): Int {
        return byteArray.contentHashCode()
    }

    fun shortString(): String {
        val s = asString
        return "${s.substring(0, 4)}:${s.substring(s.length-2, s.length)}"
    }

    override fun compareTo(other: Key): Int {
        return Arrays.compareUnsigned(this.byteArray, other.byteArray)
    }

    companion object {
        @JvmStatic
        fun fromString(str: String) = Key(str.hexStringToByteArray())
    }
}