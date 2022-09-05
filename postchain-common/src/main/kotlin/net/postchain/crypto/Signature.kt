// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.crypto

/**
 * @param subjectID is something which identifies subject which produces the
 * signature, e.g. pubkey or hash of pubkey
 * @param data is the actual signature data
 */
data class Signature(val subjectID: ByteArray, val data: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Signature

        if (!subjectID.contentEquals(other.subjectID)) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = subjectID.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
