// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.crypto

import mu.KotlinLogging
import net.postchain.common.data.Hash
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.util.Arrays
import java.math.BigInteger
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

// signing code taken from bitcoinj ECKey

val CURVE_PARAMS: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
val CURVE = ECDomainParameters(CURVE_PARAMS.curve, CURVE_PARAMS.g, CURVE_PARAMS.n, CURVE_PARAMS.h)
val HALF_CURVE_ORDER: BigInteger = CURVE_PARAMS.n.shiftRight(1)

fun bigIntegerToBytes(b: BigInteger, numBytes: Int): ByteArray {
    val bytes = ByteArray(numBytes)
    val biBytes = b.toByteArray()
    val start = if (biBytes.size == numBytes + 1) 1 else 0
    val length = Math.min(biBytes.size, numBytes)
    System.arraycopy(biBytes, start, bytes, numBytes - length, length)
    return bytes
}

/*
fun encodeSignature(r: BigInteger, s: BigInteger): ByteArray {
    val bos = ByteArrayOutputStream(72)
    val seq = DERSequenceGenerator(bos)
    seq.addObject(ASN1Integer(r))
    seq.addObject(ASN1Integer(s))
    seq.close()
    return bos.toByteArray()
}
*/

fun encodeSignature(r: BigInteger, s: BigInteger): ByteArray {
    return Arrays.concatenate(
            bigIntegerToBytes(r, 32),
            bigIntegerToBytes(s, 32)
    )
}

fun secp256k1_decodeSignature(bytes: ByteArray): Array<BigInteger> {
    val r = BigInteger(1, bytes.sliceArray(0..31))
    val s = BigInteger(1, bytes.sliceArray(32..63))
    return arrayOf(r, s)
}

/*
fun secp256k1_decodeSignature(bytes: ByteArray): Array<BigInteger> {
    var decoder: ASN1InputStream? = null
    try {
        decoder = ASN1InputStream(bytes)
        val seq = (decoder.readObject() ?: throw IllegalArgumentException("Reached past end of ASN.1 stream.")) as DLSequence
        val r: ASN1Integer
        val s: ASN1Integer
        try {
            r = seq.getObjectAt(0) as ASN1Integer
            s = seq.getObjectAt(1) as ASN1Integer
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(e)
        }
        // OpenSSL deviates from the DER spec by interpreting these values as unsigned, though they should not be
        // Thus, we always use the positive versions. See: http://r6.ca/blog/20111119T211504Z.html
        return arrayOf(r.positiveValue, s.positiveValue)
    } catch (e: IOException) {
        throw IllegalArgumentException(e)
    } finally {
        if (decoder != null)
            try {
                decoder.close()
            } catch (x: IOException) { }
    }
}*/

fun secp256k1_verify(digest: ByteArray, pubKey: ByteArray, signature: ByteArray): Boolean {
    val signer = ECDSASigner()
    val params = ECPublicKeyParameters(CURVE.curve.decodePoint(pubKey), CURVE)
    signer.init(false, params)
    return try {
        val sig = secp256k1_decodeSignature(signature)
        signer.verifySignature(digest, sig[0], sig[1])
    } catch (e: Exception) {
        logger.error(e) { "Unable to verify secp256k1 signature: ${e.message}" }
        false
    }
}

fun secp256k1_derivePubKey(privKey: ByteArray): ByteArray {
    val d = BigInteger(1, privKey)
    val q = CURVE_PARAMS.g.multiply(d)
    return q.getEncoded(true)
}

fun secp256k1_ecdh(privKey: ByteArray, pubKey: ByteArray): ByteArray {
    val d = BigInteger(1, privKey)
    val Q = CURVE.curve.decodePoint(pubKey)
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(Q.multiply(d).normalize().getEncoded(true))
}

/**
 * A factory for signatures using the elliptic curve secp256k1
 *
 * (See super class for doc)
 */
open class Secp256k1SigMaker(val pubKey: ByteArray, val privKey: ByteArray, val digestFun: (ByteArray) -> Hash) : SigMaker {
    override fun signMessage(msg: ByteArray): Signature {
        val digestedMsg = digestFun(msg)
        return signDigest(digestedMsg)
    }

    override fun signDigest(digest: Hash): Signature {
        return Signature(pubKey, sign(digest, privKey))
    }

    private fun sign(digest: ByteArray, privateKeyBytes: ByteArray): ByteArray {
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        val privateKey = BigInteger(1, privateKeyBytes)
        val privKey = ECPrivateKeyParameters(privateKey, CURVE)
        signer.init(true, privKey)
        val components = signer.generateSignature(digest)
        if (components[1] > HALF_CURVE_ORDER) {
            // canonicalize low S
            components[1] = CURVE.n.subtract(components[1])
        }
        return encodeSignature(components[0], components[1])
    }
}

/**
 * A collection of cryptographic functions based on the elliptic curve secp256k1
 */
open class Secp256K1CryptoSystem : BaseCryptoSystem() {

    /**
     * Builds logic to be used for signing data based on supplied key parameters
     *
     * @param pubKey The public key used to verify the signature
     * @param privKey The private key used to create the signature
     * @return a class to be used to sign specified data with [privKey]
     */
    @Deprecated("Pass in KeyPair instead",
            ReplaceWith("buildSigMaker(KeyPair(pubKey, privKey))", imports = ["net.postchain.crypto.KeyPair"]))
    override fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker {
        return Secp256k1SigMaker(pubKey, privKey, ::digest)
    }

    /**
     * Builds logic to be used for signing data based on supplied key parameters
     *
     * @param keyPair The key pair to create and verify the signature
     * @return a class to be used to sign specified data with [keyPair]
     */
    override fun buildSigMaker(keyPair: KeyPair): SigMaker {
        return Secp256k1SigMaker(keyPair.pubKey.data, keyPair.privKey.data, ::digest)
    }

    override fun validatePubKey(pubKey: ByteArray): Boolean = try {
        ECPublicKeyParameters(CURVE.curve.decodePoint(pubKey), CURVE)
        true
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: ArrayIndexOutOfBoundsException) {
        false
    }

    override fun derivePubKey(privKey: PrivKey): PubKey = PubKey(secp256k1_derivePubKey(privKey.data))

    /**
     * Generate a random key pair
     */
    override fun generateKeyPair(): KeyPair {
        val privKey = generatePrivKey()
        val pubKey = secp256k1_derivePubKey(privKey.data)
        return KeyPair(PubKey(pubKey), privKey)
    }

    /**
     * Generate a random private key.
     *
     * Not part of [CryptoSystem] interface since it might not make sense for other algorithms.
     */
    fun generatePrivKey(): PrivKey {
        var privateKey: ByteArray
        var d: BigInteger
        while (true) {
            privateKey = getRandomBytes(32)
            d = BigInteger(1, privateKey)
            try {
                ECPrivateKeyParameters(d, CURVE) // validate private key
                break
            } catch (e: Exception) {
                logger.debug { "Generated invalid private key: $d" }
            }
        }
        return PrivKey(privateKey)
    }
}
