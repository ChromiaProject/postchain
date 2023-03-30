package net.postchain.crypto

import net.postchain.common.exception.UserMistake
import net.postchain.crypto.DilithiumCryptoSystem.Companion.verifyDilithiumSignature
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
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

    private fun deriveSignatureVerificationFromSubject(subjectID: ByteArray): (ByteArray, ByteArray, ByteArray) -> Boolean {
        // 0x30 indicates that content is ASN1 encoded, it is also not a valid start for an ECDSA key
        return if (subjectID[0].toInt() == 0x30) {
            try {
                val keyInfo = SubjectPublicKeyInfo.getInstance(ASN1InputStream(subjectID).readObject())
                val signatureAlgorithmId = keyInfo.algorithm.algorithm
                // TODO: Investigate which parameters we should use
                if (signatureAlgorithmId == BCObjectIdentifiers.dilithium2_aes) {
                    ::verifyDilithiumSignature
                } else throw UserMistake("Unknown signature algorithm identifier $signatureAlgorithmId")
            } catch (e: Exception) {
                throw UserMistake("Invalid format of ASN1 encoded public key")
            }
        } else {
            ::secp256k1_verify
        }
    }
}
