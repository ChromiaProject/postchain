package net.postchain.crypto

import org.spongycastle.asn1.x9.X9ECParameters
import org.spongycastle.crypto.ec.CustomNamedCurves
import org.spongycastle.crypto.params.ECDomainParameters
import org.spongycastle.jcajce.provider.digest.Keccak
import org.spongycastle.math.ec.ECAlgorithms
import org.spongycastle.math.ec.ECCurve
import org.spongycastle.math.ec.ECPoint
import java.math.BigInteger

class SECP256K1Keccak {

    companion object {
        private val params: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
        private val CURVE_PARAMS = ECDomainParameters(params.curve, params.g, params.n, params.h)
        private val CURVE: ECCurve = CURVE_PARAMS.curve

        /**
         * Get ethereum address from compress public key
         */
        @JvmStatic
        fun getEthereumAddress(pubKey: ByteArray): ByteArray {
            val pub = CURVE.decodePoint(pubKey).getEncoded(false).takeLast(64).toByteArray()
            return  digest(pub).takeLast(20).toByteArray()
        }

        /**
         * Calculate the keccak256 hash digest of a message
         *
         * @param bytes A ByteArray of data consisting of the message we want the hash digest of
         * @return The keccak256 hash digest of [bytes]
         */
        @JvmStatic
        fun digest(bytes: ByteArray): ByteArray {
            return Keccak.Digest256().digest(bytes)
        }

        // implementation is based on BitcoinJ ECKey code
        // see https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/core/ECKey.java
        @JvmStatic
        fun ecrecover(recId: Int, message: ByteArray, r: BigInteger, s: BigInteger): ByteArray? {
            val n = CURVE_PARAMS.n
            // Let x = r + jn
            val i = BigInteger.valueOf((recId / 2).toLong())
            val x = r.add(i.multiply(n))

            if (x >= CURVE.order) {
                // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
                return null
            }

            // Compressed keys require you to know an extra bit of data about the y-coord as there are two possibilities.
            // So it's encoded in the recId.
            val R = decompressKey(x, recId and 1 == 1)
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
                full.takeLast(full.size-1).toByteArray()
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
            val x = CURVE.fromBigInteger(xBN)
            val alpha = x.multiply(x.square().add(CURVE.a)).add(CURVE.b)
            val beta = alpha.sqrt() ?: throw IllegalArgumentException("Invalid point compression")
            val ecPoint: ECPoint
            val nBeta = beta.toBigInteger()
            if (nBeta.testBit(0) == yBit) {
                ecPoint = CURVE.createPoint(x.toBigInteger(), nBeta);
            } else {
                val y = CURVE.fromBigInteger(CURVE.order.subtract(nBeta))
                ecPoint = CURVE.createPoint(x.toBigInteger(), y.toBigInteger())
            }
            return ecPoint
        }
    }


}