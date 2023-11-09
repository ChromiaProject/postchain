/*
 * Copyright 2013 Ken Sedgwick
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.postchain.bitcoinj.forks.crypto

import net.postchain.bitcoinj.forks.base.ByteUtils
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Objects

/**
 * A MnemonicCode object may be used to convert between binary seed values and
 * lists of words per [the BIP 39
 * specification](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)
 */
class MnemonicCode @JvmOverloads constructor() {

    private val BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db"
    /**
     * Gets the word list this code uses.
     * @return unmodifiable word list
     */
    private var wordList: List<String>? = null
    /**
     * Creates an MnemonicCode object, initializing with words read from the supplied input stream.  If a wordListDigest
     * is supplied the digest of the words will be checked.
     * @param wordstream input stream of 2048 line-seperated words
     * @param wordListDigest hex-encoded Sha256 digest to check against
     * @throws IOException if there was a problem reading the steam
     * @throws IllegalArgumentException if list size is not 2048 or digest mismatch
     */
    /** Initialise from the included word list. Won't work on Android.  */
    init {
        val md = MessageDigest.getInstance("SHA-256")
        BufferedReader(InputStreamReader(openDefaultWords(), StandardCharsets.UTF_8)).use { br ->
            wordList = br.lines()
                    .peek { word: String -> md.update(word.toByteArray()) }
                    .toList()
        }
        require(wordList!!.size == 2048) { "input stream did not contain 2048 words" }

        // If a wordListDigest is supplied check to make sure it matches.
        val digest = md.digest()
        val hexdigest: String = ByteUtils.formatHex(digest)
        require(hexdigest == BIP39_ENGLISH_SHA256) { "wordlist digest mismatch" }
    }

    @Throws(IOException::class)
    private fun openDefaultWords(): InputStream {
        return File("/Users/tim/chromaway/core/postchain/postchain-common/src/main/resources/mnemonic/wordlist/english.txt").inputStream()
    }

    private fun hash(input: ByteArray): ByteArray {
        return hash(input, 0, input.size)
    }

    fun hash(input: ByteArray?, offset: Int, length: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(input, offset, length)
        return digest.digest()
    }

    /**
     * Convert entropy data to mnemonic word list.
     * @param entropy entropy bits, length must be a multiple of 32 bits
     */
    fun toMnemonic(entropy: ByteArray): List<String> {
        check(entropy.size % 4 == 0) { "entropy length not multiple of 32 bits" }
        check(entropy.size > 0) { "entropy is empty" }

        // We take initial entropy of ENT bits and compute its
        // checksum by taking first ENT / 32 bits of its SHA256 hash.
        val hash = hash(entropy)
        val hashBits = bytesToBits(hash)
        val entropyBits = bytesToBits(entropy)
        val checksumLengthBits = entropyBits.size / 32

        // We append these bits to the end of the initial entropy.
        val concatBits = BooleanArray(entropyBits.size + checksumLengthBits)
        System.arraycopy(entropyBits, 0, concatBits, 0, entropyBits.size)
        System.arraycopy(hashBits, 0, concatBits, entropyBits.size, checksumLengthBits)

        // Next we take these concatenated bits and split them into
        // groups of 11 bits. Each group encodes number from 0-2047
        // which is a position in a wordlist.  We convert numbers into
        // words and use joined words as mnemonic sentence.
        val words = ArrayList<String>()
        val nwords = concatBits.size / 11
        for (i in 0 until nwords) {
            var index = 0
            for (j in 0..10) {
                index = index shl 1
                if (concatBits[i * 11 + j]) index = index or 0x1
            }
            words.add(wordList!![index])
        }
        return words
    }

    /**
     * Convert mnemonic word list to seed.
     */
    fun toSeed(words: List<String?>?, passphrase: String): ByteArray {
        Objects.requireNonNull(passphrase, "A null passphrase is not allowed.")

        // To create binary seed from mnemonic, we use PBKDF2 function
        // with mnemonic sentence (in UTF-8) used as a password and
        // string "mnemonic" + passphrase (again in UTF-8) used as a
        // salt. Iteration count is set to 2048 and HMAC-SHA512 is
        // used as a pseudo-random function. Desired length of the
        // derived key is 512 bits (= 64 bytes).
        //
        val pass: String = words!!.joinToString(" ")
        val salt = "mnemonic$passphrase"
        val seed = PBKDF2SHA512.derive(pass, salt, 2048, 64)
        return seed
    }

    private fun bytesToBits(data: ByteArray): BooleanArray {
        val bits = BooleanArray(data.size * 8)
        for (i in data.indices) for (j in 0..7) bits[i * 8 + j] = data[i].toInt() and (1 shl 7 - j) != 0
        return bits
    }
}
