package net.postchain.crypto.devtools

import net.postchain.crypto.*
import java.security.MessageDigest

class MockCryptoSystem : CryptoSystem {

    override fun digest(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
    }

    override fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker {
        return MockSigMaker(pubKey, privKey, ::digest)
    }

    override fun verifyDigest(ddigest: ByteArray, s: Signature): Boolean {
        return secp256k1_verify(ddigest, s.subjectID, s.data)
    }

    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            secp256k1_verify(digest(data), signature.subjectID, signature.data)
        }
    }

    override fun getRandomBytes(size: Int): ByteArray {
        return ByteArray(size)
    }
}