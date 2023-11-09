/*
 * Copyright by the original author or authors.
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
package net.postchain.bitcoinj.forks.base

import java.util.HexFormat


/**
 * Utility methods for bit, byte, and integer manipulation and conversion. Most of these were moved here
 * from `org.bitcoinj.core.Utils`.
 */
object ByteUtils {

    private val hexFormat = HexFormat.of()
    fun formatHex(bytes: ByteArray?): String {
        return hexFormat.formatHex(bytes)
    }

    /**
     * Concatentate two byte arrays
     * @param b1 first byte array
     * @param b2 second byte array
     * @return new concatenated byte array
     */
    fun concat(b1: ByteArray, b2: ByteArray): ByteArray {
        val result = ByteArray(b1.size + b2.size)
        System.arraycopy(b1, 0, result, 0, b1.size)
        System.arraycopy(b2, 0, result, b1.size, b2.size)
        return result
    }
}
