package net.postchain.crypto.devtools

import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.crypto.Verifier
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.crypto.secp256k1_verify
import java.security.MessageDigest

class MockCryptoSystem : CryptoSystem {

    override fun digest(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
    }

    @Deprecated("Pass in KeyPair instead",
            ReplaceWith("buildSigMaker(KeyPair(pubKey, privKey))", imports = ["net.postchain.crypto.KeyPair"]))
    override fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker {
        return MockSigMaker(pubKey, privKey, ::digest)
    }

    override fun buildSigMaker(keyPair: KeyPair): SigMaker {
        return MockSigMaker(keyPair.pubKey.data, keyPair.privKey.data, ::digest)
    }

    override fun verifyDigest(digest: ByteArray, s: Signature): Boolean {
        return secp256k1_verify(digest, s.subjectID, s.data)
    }

    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            secp256k1_verify(digest(data), signature.subjectID, signature.data)
        }
    }

    override fun validatePubKey(pubKey: ByteArray): Boolean = true

    override fun getRandomBytes(size: Int): ByteArray {
        return ByteArray(size)
    }

    override fun generateKeyPair(): KeyPair {
        val privKey = getRandomBytes(32)
        val pubKey = secp256k1_derivePubKey(privKey)
        return KeyPair(PubKey(pubKey), PrivKey(privKey))
    }
}