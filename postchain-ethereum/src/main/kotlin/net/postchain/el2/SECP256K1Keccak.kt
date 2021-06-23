package net.postchain.el2

import net.postchain.common.data.Hash
import net.postchain.common.data.KECCAK256
import org.spongycastle.asn1.x9.X9ECParameters
import org.spongycastle.asn1.x9.X9IntegerConverter
import org.spongycastle.crypto.ec.CustomNamedCurves
import org.spongycastle.crypto.params.ECDomainParameters
import org.spongycastle.math.ec.ECAlgorithms
import org.spongycastle.math.ec.ECCurve
import org.spongycastle.math.ec.ECPoint
import org.spongycastle.math.ec.custom.sec.SecP256K1Curve
import java.math.BigInteger

object SECP256K1Keccak {

    private val params: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
    private val CURVE_PARAMS = ECDomainParameters(params.curve, params.g, params.n, params.h)
    private val CURVE: ECCurve = CURVE_PARAMS.curve

    /**
     * Get ethereum address from compress public key
     */
    fun getEthereumAddress(pubKey: ByteArray): ByteArray {
        val pub = CURVE.decodePoint(pubKey).getEncoded(false).takeLast(64).toByteArray()
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
}