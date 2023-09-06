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
        val verifyFn = deriveSignatureVerification(s.subjectID)
        return verifyFn(digest, s.subjectID, s.data)
    }

    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            val verifyFn = deriveSignatureVerification(signature.subjectID)
            verifyFn(digest(data), signature.subjectID, signature.data)
        }
    }

    /**
     * This function can choose the correct signature algorithm implementation based on the public key.
     *
     * @param subjectID The public key to derive what signature algorithm to use
     */
    // TODO need to do something more clever here if we want to have more than two crypto systems
    private fun deriveSignatureVerification(subjectID: ByteArray): BasicVerifier = deriveSignatureVerificationFromSubject(subjectID)
            ?: ::secp256k1_verify

    protected abstract fun deriveSignatureVerificationFromSubject(subjectID: ByteArray): BasicVerifier?
}
