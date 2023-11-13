/*
 * Copyright 2013 Matija Mazi.
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

import java.util.Arrays;

/**
 * Code copied from: https://github.com/bitcoinj/bitcoinj/blob/v0.16.1/core/src/main/java/org/bitcoinj/crypto/HDKeyDerivation.java
 * Copied code is under Apache License, the same license that is included in this file
 * Changes:
 *  - Have removed unneeded code
 *  - Return of method createMasterPrivateKey returns byte[] instead of org.bitcoinj.crypto.DeterministicKey and we
 */

public final class HDKeyDerivation {

    public static byte[] createMasterPrivateKey(byte[] seed) {
        if (seed.length < 8) {
            throw new IllegalArgumentException("Seed is too short and could be brute forced");
        }
        // Calculate I = HMAC-SHA512(key="Bitcoin seed", msg=S)
        byte[] i = HDUtils.hmacSha512(HDUtils.createHmacSha512Digest("Bitcoin seed".getBytes()), seed);
        // Split I into two 32-byte sequences, Il and Ir.
        // Use Il as master secret key, and Ir as master chain code.
        if (i.length != 64) {
            throw new IllegalStateException("Bytes must be of length 64");
        }
        byte[] il = Arrays.copyOfRange(i, 0, 32);

        if (il.length != 32) {
            throw new IllegalStateException("Couldn't create a private key of 32 bytes from seed");
        }
        return il;
    }
}
