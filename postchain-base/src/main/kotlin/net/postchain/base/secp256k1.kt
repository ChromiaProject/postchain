// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.data.Hash
import net.postchain.common.data.KECCAK256
import net.postchain.core.Signature
import net.postchain.gtx.EMPTY_SIGNATURE
import org.spongycastle.asn1.x9.X9IntegerConverter
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.ec.CustomNamedCurves
import org.spongycastle.crypto.params.ECDomainParameters
import org.spongycastle.crypto.params.ECPrivateKeyParameters
import org.spongycastle.crypto.params.ECPublicKeyParameters
import org.spongycastle.crypto.signers.ECDSASigner
import org.spongycastle.crypto.signers.HMacDSAKCalculator
import org.spongycastle.math.ec.ECAlgorithms
import org.spongycastle.math.ec.ECPoint
import org.spongycastle.math.ec.custom.sec.SecP256K1Curve
import org.spongycastle.util.Arrays
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom


// signing code taken from bitcoinj ECKey

val CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1")!!
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

fun encodeSignature(r: BigInteger, s: BigInteger, v: Int): ByteArray {
    return Arrays.concatenate(
        bigIntegerToBytes(r, 32),
        bigIntegerToBytes(s, 32),
        ByteBuffer.allocate(1).put(v.toByte()).array()
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

fun secp256k1_sign(digest: ByteArray, privateKeyBytes: ByteArray): ByteArray {
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

fun secp256k1_verify(digest: ByteArray, pubKey: ByteArray, signature: ByteArray): Boolean {
    val signer = ECDSASigner()
    val params = ECPublicKeyParameters(CURVE.curve.decodePoint(pubKey), CURVE)
    signer.init(false, params)
    try {
        val sig = secp256k1_decodeSignature(signature)
        return signer.verifySignature(digest, sig[0], sig[1])
    } catch (e: Exception) {
        e.printStackTrace()
        return false
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
 * @param hash signed message
 * @param pubKey compress public key
 * @param signature signature without v
 * @return signature with v to run ecrecover properly on ethereum solidity smart contract
 */
fun encodeSignatureWithV(hash: ByteArray, pubKey: ByteArray, signature: ByteArray): ByteArray {
    val pub = decompressKey(pubKey)
    val sig = secp256k1_decodeSignature(signature)
    val pub0 = ecrecover(0, hash, sig[0], sig[1])
    if (Arrays.areEqual(pub0, pub)) {
        return encodeSignature(sig[0], sig[1], 27)
    }
    val pub1 = ecrecover(1, hash, sig[0], sig[1])
    if (Arrays.areEqual(pub1, pub)) {
        return encodeSignature(sig[0], sig[1], 28)
    }
    return EMPTY_SIGNATURE
}

/**
 * @param pubKey public key
 * @return uncompress public key (64 bytes)
 */
fun decompressKey(pubKey: ByteArray): ByteArray {
    if (pubKey.size == 64) {
        return pubKey
    }
    return CURVE.curve.decodePoint(pubKey).getEncoded(false).takeLast(64).toByteArray()
}

/**
 * Get ethereum address from compress public key
 */
fun getEthereumAddress(pubKey: ByteArray): ByteArray {
    val pub = CURVE.curve.decodePoint(pubKey).getEncoded(false).takeLast(64).toByteArray()
    return digest(pub).takeLast(20).toByteArray()
}

fun toChecksumAddress(address: String): String {
    val lowercaseAddress = Numeric.cleanHexPrefix(address).toLowerCase()
    val addressHash = Numeric.cleanHexPrefix(Hashes.sha3String(lowercaseAddress))
    val result = StringBuilder(lowercaseAddress.length + 2)
    for (i in lowercaseAddress.indices) {
        if (addressHash[i].toString().toInt(16) >= 8) {
            result.append(lowercaseAddress[i].toString().toUpperCase())
        } else {
            result.append(lowercaseAddress[i])
        }
    }
    return Numeric.prependHexPrefix(result.toString())
}

/**
 * Calculate the keccak256 hash digest of a message
 *
 * @param bytes A ByteArray of data consisting of the message we want the hash digest of
 * @return The keccak256 hash digest of [bytes]
 */
fun digest(bytes: ByteArray): Hash {
    val m = MessageDigestFactory.create(KECCAK256)
    return m.digest(bytes)
}

// implementation is based on BitcoinJ ECKey code
// see https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/core/ECKey.java
fun ecrecover(recId: Int, message: ByteArray, r: BigInteger, s: BigInteger): ByteArray? {
    val n = CURVE_PARAMS.n
    // Let x = r + jn
    val i = BigInteger.valueOf((recId / 2).toLong())
    val x = r.add(i.multiply(n))

    if (x >= SecP256K1Curve.q) {
        // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
        return null
    }

    // Compressed keys require you to know an extra bit of data about the y-coord as there are two possibilities.
    // So it's encoded in the recId.
    val R = decompressKey(x, (recId and 1) == 1)
    if (!R.multiply(n).isInfinity) {
        // If nR != point at infinity, then recId (i.e. v) is invalid
        return null
    }

    //
    // Compute a candidate public key as:
    // Q = mi(r) * (sR - eG)
    //
    // Where mi(x) is the modular multiplicative inverse. We transform this into the following:
    // Q = (mi(r) * s ** R) + (mi(r) * -e ** G)
    // Where -e is the modular additive inverse of e, that is z such that z + e = 0 (mod n).
    // In the above equation, ** is point multiplication and + is point addition (the EC group operator).
    //
    // We can find the additive inverse by subtracting e from zero then taking the mod. For example the additive
    // inverse of 3 modulo 11 is 8 because 3 + 8 mod 11 = 0, and -3 mod 11 = 8.
    //
    val e = BigInteger(1, message)
    val eInv = BigInteger.ZERO.subtract(e).mod(n)
    val rInv = r.modInverse(n)
    val srInv = rInv.multiply(s).mod(n)
    val eInvrInv = rInv.multiply(eInv).mod(n)

    return try {
        val q = ECAlgorithms.sumOfTwoMultiplies(CURVE_PARAMS.g, eInvrInv, R, srInv)

        // For Ethereum we don't use first byte of the key
        val full = q.getEncoded(false)
        full.takeLast(64).toByteArray()
    } catch (e: Exception) {
        null
    }
}

/**
 * Decompress a compressed public key (x coordinate and low-bit of y-coordinate).
 *
 * @param xBN X-coordinate
 * @param yBit Sign of Y-coordinate
 * @return Uncompressed public key
 */
private fun decompressKey(xBN: BigInteger, yBit: Boolean): ECPoint {
    val x9 = X9IntegerConverter()
    val compEnc: ByteArray = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE_PARAMS.curve))
    compEnc[0] = (if (yBit) 0x03 else 0x02).toByte()
    return CURVE_PARAMS.curve.decodePoint(compEnc)
}

/**
 * A factory for signatures using the elliptic curve secp256k1
 *
 * (See super class for doc)
 */
class Secp256k1SigMaker(val pubKey: ByteArray, val privKey: ByteArray, val digestFun: (ByteArray) -> Hash) : SigMaker {
    override fun signMessage(msg: ByteArray): Signature {
        val digestedMsg = digestFun(msg)
        return signDigest(digestedMsg)
    }

    override fun signDigest(digest: Hash): Signature {
        return Signature(pubKey, secp256k1_sign(digest, privKey))
    }
}

/**
 * A collection of cryptographic functions based on the elliptic curve secp256k1
 */
class SECP256K1CryptoSystem : CryptoSystem {
    private val rand = SecureRandom()

    /**
     * Calculate the hash digest of a message
     *
     * @param bytes A ByteArray of data consisting of the message we want the hash digest of
     * @return The hash digest of [bytes]
     */
    override fun digest(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
    }

    /**
     * Builds logic to be used for signing data based on supplied key parameters
     *
     * @param pubkey The public key used to verify the signature
     * @param privKey The private key used to create the signature
     * @return a class to be used to sign specified [data] with [privkey]
     */
    override fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker {
        return Secp256k1SigMaker(pubKey, privKey, ::digest)
    }

    /**
     * Verify a signature from hash digest of a message
     *
     * @param digest The hash digest of the message we want to verify the signature [s] for
     * @param s The signature to verify
     * @return True or false depending on the outcome of the verification
     */
    override fun verifyDigest(digest: ByteArray, s: Signature): Boolean {
        return secp256k1_verify(digest, s.subjectID, s.data)
    }

    /**
     * Create a function to be used for verifying a signature based on some data
     *
     * @return A function that will take a signature and some data and return a boolean
     */
    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            secp256k1_verify(digest(data), signature.subjectID, signature.data)
        }
    }

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
}