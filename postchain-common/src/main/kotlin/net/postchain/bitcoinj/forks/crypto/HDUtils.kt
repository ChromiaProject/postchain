/*
 * Copyright 2013 Matija Mazi.
 * Copyright 2014 Giannis Dzegoutanis.
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

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Code copied from: https://github.com/bitcoinj/bitcoinj/blob/v0.16.1/core/src/main/java/org/bitcoinj/crypto/HDUtils.java
 * Copied code is under Apache License, the same license that is included in this file
 * Changes:
 *  - Have removed unneeded code
 *  - Converted from Java to Kotlin
 */

object HDUtils {
    fun createHmacSha512Digest(key: ByteArray?): HMac {
        val digest = SHA512Digest()
        val hMac = HMac(digest)
        hMac.init(KeyParameter(key))
        return hMac
    }

    fun hmacSha512(hmacSha512: HMac, input: ByteArray): ByteArray {
        hmacSha512.reset()
        hmacSha512.update(input, 0, input.size)
        val out = ByteArray(64)
        hmacSha512.doFinal(out, 0)
        return out
    }
}
