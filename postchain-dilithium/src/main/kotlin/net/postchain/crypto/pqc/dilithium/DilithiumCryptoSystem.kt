package net.postchain.crypto.pqc.dilithium

import net.postchain.common.exception.UserMistake
import net.postchain.crypto.BaseCryptoSystem
import net.postchain.crypto.BasicVerifier
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters
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

    private val dilithiumParameters = DilithiumParameters.dilithium2 // TODO: Investigate which parameters we should use

    init {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(BouncyCastlePQCProvider())
        }
    }

    @Deprecated("Pass in KeyPair instead",
            ReplaceWith("buildSigMaker(KeyPair(pubKey, privKey))", imports = ["net.postchain.crypto.KeyPair"]))
    override fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker =
            buildSigMaker(KeyPair(PubKey(pubKey), PrivKey(privKey)))

    override fun buildSigMaker(keyPair: KeyPair): SigMaker = DilithiumSigMaker(keyPair, ::digest)

    override fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER).apply {
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

    // 0x30 indicates that content is ASN1 encoded, it is also not a valid start for an ECDSA key
    override fun deriveSignatureVerificationFromSubject(subjectID: ByteArray): BasicVerifier? =
            if (subjectID[0].toInt() == 0x30) {
                try {
                    val keyInfo = SubjectPublicKeyInfo.getInstance(ASN1InputStream(subjectID).readObject())
                    val signatureAlgorithmId = keyInfo.algorithm.algorithm
                    // TODO: Investigate which parameters we should use
                    if (signatureAlgorithmId == BCObjectIdentifiers.dilithium2) {
                        ::verifyDilithiumSignature
                    } else throw UserMistake("Unknown signature algorithm identifier $signatureAlgorithmId")
                } catch (e: Exception) {
                    throw UserMistake("Invalid format of ASN1 encoded public key")
                }
            } else {
                null
            }

    companion object {
        const val ALGORITHM = "Dilithium"
        const val PROVIDER = "BCPQC"

        fun verifyDilithiumSignature(digest: ByteArray, subjectID: ByteArray, data: ByteArray): Boolean {
            if (Security.getProvider(PROVIDER) == null) {
                Security.addProvider(BouncyCastlePQCProvider())
            }

            val signer = java.security.Signature.getInstance(ALGORITHM, PROVIDER).apply {
                initVerify(decodePublicKey(PubKey(subjectID)))
                update(digest)
            }
            return signer.verify(data)
        }

        fun decodePrivateKey(privKey: PrivKey): PrivateKey = PKCS8EncodedKeySpec(privKey.data).let {
            KeyFactory.getInstance(ALGORITHM, PROVIDER).generatePrivate(it)
        }

        private fun decodePublicKey(pubKey: PubKey): PublicKey = X509EncodedKeySpec(pubKey.data).let {
            KeyFactory.getInstance(ALGORITHM, PROVIDER).generatePublic(it)
        }
    }
}
