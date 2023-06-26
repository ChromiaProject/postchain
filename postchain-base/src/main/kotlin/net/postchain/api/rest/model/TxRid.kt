// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.model

import net.postchain.common.toHex

class TxRid(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "Hash must be exactly 32 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) return true
        if (other !is TxRid) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = bytes.toHex()
}
