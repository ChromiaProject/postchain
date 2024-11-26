
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

package net.postchain.bitcoinj.forks.crypto;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static kotlin.jvm.internal.Intrinsics.checkNotNull;


/**
 * Code copied from: https://github.com/bitcoinj/bitcoinj/blob/v0.16.1/core/src/main/java/org/bitcoinj/crypto/MnemonicCode.java
 * Copied code is under Apache License, the same license that is included in this file
 * Changes:
 *  - Have removed unneeded code
 *  - Refactored hex formatting to method. line 107 in original file
 *  - Added hash function in this class directly
 *  - Change class to non-static
 */

public class MnemonicCode {
    private ArrayList<String> wordList;
    private static final String BIP39_ENGLISH_RESOURCE_NAME = "/mnemonic/wordlist/english.txt";
    private static final String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";

    private static final int PBKDF2_ROUNDS = 2048;

    public MnemonicCode() throws IOException, NoSuchAlgorithmException {
        this(openDefaultWords(), BIP39_ENGLISH_SHA256);
    }

    private static InputStream openDefaultWords() throws IOException {
        InputStream stream = MnemonicCode.class.getResourceAsStream(BIP39_ENGLISH_RESOURCE_NAME);
        if (stream == null)
            throw new FileNotFoundException(BIP39_ENGLISH_RESOURCE_NAME);
        return stream;
    }

    /**
     * Creates an MnemonicCode object, initializing with words read from the supplied input stream.  If a wordListDigest
     * is supplied the digest of the words will be checked.
     */
    public MnemonicCode(InputStream wordstream, String wordListDigest) throws IOException, IllegalArgumentException, NoSuchAlgorithmException {
        BufferedReader br = new BufferedReader(new InputStreamReader(wordstream, StandardCharsets.UTF_8));
        this.wordList = new ArrayList<>(2048);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String word;
        while ((word = br.readLine()) != null) {
            md.update(word.getBytes());
            this.wordList.add(word);
        }
        br.close();

        if (this.wordList.size() != 2048)
            throw new IllegalArgumentException("input stream did not contain 2048 words");

        // If a wordListDigest is supplied check to make sure it matches.
        if (wordListDigest != null) {
            byte[] digest = md.digest();
            String hexdigest = formatHex(digest);
            if (!hexdigest.equals(wordListDigest))
                throw new IllegalArgumentException("wordlist digest mismatch");
        }
    }

    /**
     * Convert mnemonic word list to seed.
     */
    public byte[] toSeed(List<String> words, String passphrase) {
        checkNotNull(passphrase, "A null passphrase is not allowed.");

        // To create binary seed from mnemonic, we use PBKDF2 function
        // with mnemonic sentence (in UTF-8) used as a password and
        // string "mnemonic" + passphrase (again in UTF-8) used as a
        // salt. Iteration count is set to 4096 and HMAC-SHA512 is
        // used as a pseudo-random function. Desired length of the
        // derived key is 512 bits (= 64 bytes).
        //
        String pass = String.join(" ", words);
        String salt = "mnemonic" + passphrase;

        byte[] seed = PBKDF2SHA512.derive(pass, salt, PBKDF2_ROUNDS, 64);
        return seed;
    }

    /**
     * Convert entropy data to mnemonic word list.
     */
    public List<String> toMnemonic(byte[] entropy) throws NoSuchAlgorithmException {
        if (entropy.length % 4 > 0)
            throw new IllegalArgumentException("Entropy length not multiple of 32 bits.");

        if (entropy.length == 0)
            throw new IllegalArgumentException("Entropy is empty.");

        // We take initial entropy of ENT bits and compute its
        // checksum by taking first ENT / 32 bits of its SHA256 hash.

        byte[] hash = hash(entropy);
        boolean[] hashBits = bytesToBits(hash);

        boolean[] entropyBits = bytesToBits(entropy);
        int checksumLengthBits = entropyBits.length / 32;

        // We append these bits to the end of the initial entropy.
        boolean[] concatBits = new boolean[entropyBits.length + checksumLengthBits];
        System.arraycopy(entropyBits, 0, concatBits, 0, entropyBits.length);
        System.arraycopy(hashBits, 0, concatBits, entropyBits.length, checksumLengthBits);

        // Next we take these concatenated bits and split them into
        // groups of 11 bits. Each group encodes number from 0-2047
        // which is a position in a wordlist.  We convert numbers into
        // words and use joined words as mnemonic sentence.

        ArrayList<String> words = new ArrayList<>();
        int nwords = concatBits.length / 11;
        for (int i = 0; i < nwords; ++i) {
            int index = 0;
            for (int j = 0; j < 11; ++j) {
                index <<= 1;
                if (concatBits[(i * 11) + j])
                    index |= 0x1;
            }
            words.add(this.wordList.get(index));
        }

        return words;
    }

    private byte[] hash(byte[] input) throws NoSuchAlgorithmException {
        return hash(input, 0, input.length);
    }

    private byte[] hash(byte[] input, int offset, int length) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(input, offset, length);
        return digest.digest();
    }

    private static boolean[] bytesToBits(byte[] data) {
        boolean[] bits = new boolean[data.length * 8];
        for (int i = 0; i < data.length; ++i)
            for (int j = 0; j < 8; ++j)
                bits[(i * 8) + j] = (data[i] & (1 << (7 - j))) != 0;
        return bits;
    }

    private String formatHex(byte[] bytes) {
        var hexFormat = HexFormat.of();
        return hexFormat.formatHex(bytes);
    }
}
