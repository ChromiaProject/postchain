package net.postchain.crypto

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.HASH_LENGTH
import net.postchain.common.data.Hash
import java.security.InvalidParameterException
import java.security.MessageDigest

interface DigestSystem {
    val algorithm: String
    val md: MessageDigest

    fun hash(left: Hash, right: Hash): Hash
    fun digest(data: ByteArray): Hash
}

class EthereumL2DigestSystem(override val algorithm: String) : DigestSystem {

    override val md = MessageDigestFactory.create(algorithm)

    override fun hash(left: Hash, right: Hash): Hash {
        if (left.size != HASH_LENGTH || right.size != HASH_LENGTH)
            throw InvalidParameterException("invalid hash length")
        return when {
            left.contentEquals(EMPTY_HASH) && right.contentEquals(EMPTY_HASH) -> EMPTY_HASH
            left.contentEquals(EMPTY_HASH) -> right
            right.contentEquals(EMPTY_HASH) -> left
            else -> digest(left.plus(right))
        }
    }

    override fun digest(data: ByteArray): Hash {
        return md.digest(data)
    }

}

