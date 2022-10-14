// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.HASH_LENGTH
import net.postchain.common.data.Hash
import java.security.InvalidParameterException
import java.security.MessageDigest

interface DigestSystem {
    val messageDigest: MessageDigest

    fun hash(left: Hash, right: Hash): Hash
    fun digest(data: ByteArray): Hash
}


class SimpleDigestSystem(override val messageDigest: MessageDigest) : DigestSystem {

    override fun hash(left: Hash, right: Hash): Hash {
        if (left.size != HASH_LENGTH || right.size != HASH_LENGTH)
            throw InvalidParameterException("invalid hash length")
        return when {
            left.contentEquals(EMPTY_HASH) && right.contentEquals(EMPTY_HASH) -> EMPTY_HASH
            left.contentEquals(EMPTY_HASH) -> digest(right)
            right.contentEquals(EMPTY_HASH) -> digest(left)
            else -> digest(left.plus(right))
        }
    }

    override fun digest(data: ByteArray): Hash {
        return messageDigest.digest(data)
    }

}