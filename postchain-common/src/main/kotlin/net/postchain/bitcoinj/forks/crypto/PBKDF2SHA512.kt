/*
 * Copyright (c) 2012 Cole Barnes [cryptofreek{at}gmail{dot}com]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package net.postchain.bitcoinj.forks.crypto

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Code copied from: https://github.com/bitcoinj/bitcoinj/blob/v0.16.1/core/src/main/java/org/bitcoinj/crypto/MnemonicCode.java
 * Copied code was under the same copyright license as in this file
 * Changes:
 *  - Have removed unneeded code
 *  - Converted from Java to Kotlin
 *  - Refactored concat to method. Line 84 in original file
 */
object PBKDF2SHA512 {
    // Length of HMAC result
    private const val H_LEN = 64

    /**
     * Derive a key using PBKDF2-SHA512
     * @param P password
     * @param S salt
     * @param c iteration count, a positive integer
     * @param dkLen intended length in octets of the derived key, a positive integer
     * @return derived key
     */
    fun derive(P: String, S: String, c: Int, dkLen: Int): ByteArray {
        check(c > 0) { "count must be greater than zero" }
        check(dkLen > 0 ) { "derived key length must be greater than zero" }
        val baos = ByteArrayOutputStream()

        // The algorithm in RFC 2898 section 5.2, says to check `dkLen` is not greater than (2^32 - 1) * `H_LEN`
        // But that is not possible given `dkLen` is an `int` argument, so we omit the check.
        try {
            val l = (dkLen + H_LEN - 1) / H_LEN // Divide by H_LEN with rounding up
            // int r = dkLen - (l-1)*hLen;
            for (i in 1..l) {
                val T = F(P, S, c, i)
                baos.write(T)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            throw RuntimeException(e)
        }
        val baDerived = ByteArray(dkLen)
        System.arraycopy(baos.toByteArray(), 0, baDerived, 0, baDerived.size)
        return baDerived
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    private fun F(P: String, S: String, c: Int, i: Int): ByteArray? {
        var U_LAST: ByteArray? = null
        var U_XOR: ByteArray? = null
        val key = SecretKeySpec(P.toByteArray(StandardCharsets.UTF_8), "HmacSHA512")
        val mac = Mac.getInstance(key.algorithm)
        mac.init(key)
        for (j in 0 until c) {
            if (j == 0) {
                val baU: ByteArray = concat(S.toByteArray(StandardCharsets.UTF_8), INT(i))
                U_XOR = mac.doFinal(baU)
                U_LAST = U_XOR
            } else {
                val baU = mac.doFinal(U_LAST)
                for (k in U_XOR!!.indices) {
                    U_XOR[k] = (U_XOR[k].toInt() xor baU[k].toInt()).toByte()
                }
                U_LAST = baU
            }
        }
        return U_XOR
    }

    private fun INT(i: Int): ByteArray {
        return ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(i)
                .array()
    }

    private fun concat(b1: ByteArray, b2: ByteArray): ByteArray {
        val result = ByteArray(b1.size + b2.size)
        System.arraycopy(b1, 0, result, 0, b1.size)
        System.arraycopy(b2, 0, result, b1.size, b2.size)
        return result
    }
}
