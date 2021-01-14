package net.postchain.utils

import org.spongycastle.jcajce.provider.digest.Keccak
import java.nio.charset.StandardCharsets


class Hashes {
    companion object {
        /**
         * Keccak-256 hash function.
         *
         * @param input binary encoded input data
         * @param offset of start of data
         * @param length of data
         * @return hash value
         */
        fun sha3(input: ByteArray?, offset: Int, length: Int): ByteArray {
            val k: Keccak.DigestKeccak = Keccak.Digest256()
            k.update(input, offset, length)
            return k.digest()
        }

        /**
         * Keccak-256 hash function.
         *
         * @param input binary encoded input data
         * @return hash value
         */
        fun sha3(input: ByteArray): ByteArray {
            return sha3(input, 0, input.size)
        }

        /**
         * Keccak-256 hash function that operates on a UTF-8 encoded String.
         *
         * @param utf8String UTF-8 encoded string
         * @return hash value as hex encoded string
         */
        fun sha3String(utf8String: String): String {
            return Numeric.toHexString(sha3(utf8String.toByteArray(StandardCharsets.UTF_8)))
        }
    }
}