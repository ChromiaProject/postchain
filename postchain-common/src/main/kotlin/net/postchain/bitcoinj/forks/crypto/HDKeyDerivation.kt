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

package net.postchain.bitcoinj.forks.crypto

import java.util.Arrays

/**
 * Code copied from: https://github.com/bitcoinj/bitcoinj/blob/v0.16.1/core/src/main/java/org/bitcoinj/crypto/HDKeyDerivation.java
 * Copied code is under Apache License, the same license that is included in this file
 * Changes:
 *  - Have removed unneeded code
 *  - Converted from Java to Kotlin
 *  - Return of method createMasterPrivateKey returns ByteArray instead of org.bitcoinj.crypto.DeterministicKey
 */

object HDKeyDerivation {

    fun createMasterPrivateKey(seed: ByteArray): ByteArray {
        check(seed.size > 8) { "Seed is too short and could be brute forced" }
        val i = HDUtils.hmacSha512(HDUtils.createHmacSha512Digest("Bitcoin seed".toByteArray()), seed)
        check(i.size == 64) { i.size }
        val il = Arrays.copyOfRange(i, 0, 32)
        check(il.size == 32) {"Couldn't create private key of 32 bytes from seed"}
        return il
    }
}
