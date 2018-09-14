// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

import net.postchain.common.toHex

class ByteArrayKey(private val byteArray: ByteArray) {

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
}

/**
 * Returns [ByteArrayKey] for given [ByteArray] object
 */
fun ByteArray.byteArrayKeyOf() =
        ByteArrayKey(this)

