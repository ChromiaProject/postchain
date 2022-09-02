// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.crypto

import java.util.*

/**
 * @param subjectID something which identifies subject which produces the signature, eg pubkey or hash of pubkey
 * @param data signature
 */
data class Signature(val subjectID: ByteArray, val data: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Signature

        if (!Arrays.equals(subjectID, other.subjectID)) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(subjectID)
        result = 31 * result + Arrays.hashCode(data)
        return result
    }
}
