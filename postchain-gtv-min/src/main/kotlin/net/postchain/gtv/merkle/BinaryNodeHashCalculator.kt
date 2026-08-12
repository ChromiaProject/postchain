package net.postchain.gtv.merkle

import net.postchain.common.data.Hash
import net.postchain.crypto.Digester

public abstract class BinaryNodeHashCalculator(public val digester: Digester?) {

    public abstract fun calculateNodeHash(prefix: Byte, hashLeft: Hash, hashRight: Hash): Hash

    /**
     * We smack on the prefix before hashing.
     */
    protected fun calculateNodeHashInternal(
        prefix: Byte,
        hashLeft: Hash,
        hashRight: Hash,
        hashFun: (ByteArray, Digester?) -> Hash,
    ): Hash {
        val byteArraySum = byteArrayOf(prefix) + hashLeft + hashRight
        return hashFun(byteArraySum, digester)
    }
}
