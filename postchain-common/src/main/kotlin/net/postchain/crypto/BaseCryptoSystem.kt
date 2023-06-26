package net.postchain.crypto

import java.security.SecureRandom

abstract class BaseCryptoSystem : CryptoSystem {
    protected val rand = SecureRandom()

    /**
     * Calculate the hash digest of a message
     *
     * @param bytes A ByteArray of data consisting of the message we want the hash digest of
     * @return The hash digest of [bytes]
     */
    override fun digest(bytes: ByteArray): ByteArray = sha256Digest(bytes)

    /**
     * Generate some amount of random bytes
     *
     * @param size The number of bytes to generate
     * @return The random bytes in a ByteArray
     */
    //TODO: Is it really secure to use SecureRandom()? Needs more research.
    override fun getRandomBytes(size: Int): ByteArray {
        val ret = ByteArray(size)
        rand.nextBytes(ret)
        return ret
    }

    override fun verifyDigest(digest: ByteArray, s: Signature): Boolean {
        val verifyFn = deriveSignatureVerificationFromSubject(s.subjectID)
        return verifyFn(digest, s.subjectID, s.data)
    }

    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            val verifyFn = deriveSignatureVerificationFromSubject(signature.subjectID)
            verifyFn(digest(data), signature.subjectID, signature.data)
        }
    }

    protected abstract fun deriveSignatureVerificationFromSubject(subjectID: ByteArray): (ByteArray, ByteArray, ByteArray) -> Boolean

}
