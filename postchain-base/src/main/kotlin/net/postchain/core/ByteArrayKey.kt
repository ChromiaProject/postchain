// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.common.toHex
import net.postchain.crypto.Key
import org.spongycastle.util.Arrays

@Deprecated("Use Key from crypto module in stead", ReplaceWith("Key(byteArray)", "net.postchain.crypto.Key"))
class ByteArrayKey(val byteArray: ByteArray): Comparable<ByteArrayKey> {

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (super.equals(other)) return true
        return (other as? ByteArrayKey)?.byteArray?.contentEquals(byteArray) ?: false
    }

    override fun hashCode(): Int {
        return byteArray.contentHashCode()
    }

    override fun toString(): String {
        return byteArray.toHex()
    }

    fun shortString(): String {
        val s = toString()
        return "${s.substring(0, 4)}:${s.substring(s.length-2, s.length)}"
    }

    override fun compareTo(other: ByteArrayKey): Int {
        return Arrays.compareUnsigned(this.byteArray, other.byteArray)
    }
}

/**
 * Returns [Key] for given [ByteArray] object
 */
@Deprecated("Use Key directly from crypto module in stead", ReplaceWith("Key(this)", "net.postchain.crypto.Key"))
fun ByteArray.byteArrayKeyOf() =
        Key(this)

