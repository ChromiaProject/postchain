// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.merkle

import net.postchain.base.CryptoSystem

abstract class BinaryNodeHashCalculator(val cryptoSystem: CryptoSystem?) {

    abstract fun calculateNodeHash(prefix: Byte, hashLeft: Hash, hashRight: Hash, includePrefix: Boolean = true): Hash

    /**
     * We smack on the prefix before hashing.
     *
     * @param prefix the one byte prefix to use
     * @param hashLeft The hash of the left sub tree
     * @param hashRight The hash of the right sub tree
     * @param hashFun The only reason we pass the function as a parameter is to simplify testing.
     * @param includePrefix include prefix in the hash calculation or not, default is true
     * @return the hash of two combined hashes.
     */
    protected fun calculateNodeHashInternal(prefix: Byte, hashLeft: Hash, hashRight: Hash, hashFun: (ByteArray, CryptoSystem?) -> Hash, includePrefix: Boolean = true): Hash {
        val byteArraySum = if (includePrefix)
            byteArrayOf(prefix) + hashLeft + hashRight
        else
            hashLeft + hashRight
        return hashFun(byteArraySum, cryptoSystem)
    }
}