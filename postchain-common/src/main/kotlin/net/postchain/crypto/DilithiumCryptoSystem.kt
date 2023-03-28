package net.postchain.crypto

import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters.dilithium2_aes
import org.bouncycastle.pqc.jcajce.interfaces.DilithiumPrivateKey
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class DilithiumCryptoSystem : BaseCryptoSystem() {
    private val dilithiumParameters = dilithium2_aes // TODO: Investigate which parameters we should use, if we need dilithium5 we have to change DB schema

    init {
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(BouncyCastlePQCProvider())
        }
    }

    @Deprecated("Pass in KeyPair instead",
            ReplaceWith("buildSigMaker(KeyPair(pubKey, privKey))", imports = ["net.postchain.crypto.KeyPair"]))
    override fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker =
            buildSigMaker(KeyPair(PubKey(pubKey), PrivKey(privKey)))

    override fun buildSigMaker(keyPair: KeyPair): SigMaker = DilithiumSigMaker(keyPair, ::digest)

    override fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("Dilithium", "BCPQC").apply {
            initialize(DilithiumParameterSpec.fromName(dilithiumParameters.name), rand)
        }

        val keyPair = keyPairGenerator.generateKeyPair()
        return KeyPair(keyPair.public.encoded, keyPair.private.encoded)
    }

    override fun validatePubKey(pubKey: ByteArray): Boolean = try {
        decodePublicKey(PubKey(pubKey))
        true
    } catch (_: InvalidKeySpecException) {
        false
    }

    override fun derivePubKey(privKey: PrivKey): PubKey {
        val decodedPrivateKey = decodePrivateKey(privKey)
        val decodedPublicKey = (decodedPrivateKey as DilithiumPrivateKey).publicKey
        return PubKey(decodedPublicKey.encoded)
    }


    companion object {
        fun verifyDilithiumSignature(digest: ByteArray, subjectID: ByteArray, data: ByteArray): Boolean {
            if (Security.getProvider("BCPQC") == null) {
                Security.addProvider(BouncyCastlePQCProvider())
            }

            val signer = java.security.Signature.getInstance("Dilithium", "BCPQC").apply {
                initVerify(decodePublicKey(PubKey(subjectID)))
                update(digest)
            }
            return signer.verify(data)
        }

        fun decodePrivateKey(privKey: PrivKey): PrivateKey = PKCS8EncodedKeySpec(privKey.data).let {
            KeyFactory.getInstance("Dilithium", "BCPQC").generatePrivate(it)
        }

        private fun decodePublicKey(pubKey: PubKey): PublicKey = X509EncodedKeySpec(pubKey.data).let {
            KeyFactory.getInstance("Dilithium", "BCPQC").generatePublic(it)
        }
    }
}
