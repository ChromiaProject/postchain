// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.core.block.hexStringLength
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable

data class TxDetail(
        @Name("rid") val rid: ByteArray,
        @Name("hash") val hash: ByteArray,
        @Name("data") @Nullable val data: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxDetail

        if (!rid.contentEquals(other.rid)) return false
        if (!hash.contentEquals(other.hash)) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rid.contentHashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

fun TxDetail.hexStringLength(): Int {
    val ridSize = rid.hexStringLength()
    val hashSize = hash.hexStringLength()
    val dataSize = data.hexStringLength()

    return ridSize + hashSize + dataSize
}

open class TransactionInfoExt(
    val blockRID: ByteArray,
    val blockHeight: Long,
    val blockHeader: ByteArray,
    val witness: ByteArray,
    val timestamp: Long,
    val txRID: ByteArray,
    val txHash: ByteArray,
    val txData: ByteArray?
)

data class TransactionInfoExtsTruncated(
        val transactionInfoExts: List<TransactionInfoExt>,
        val truncated: Boolean
)

data class ValidationResult(
        val result: Result,
        val message: String = "") {
    enum class Result {
        OK, PREV_BLOCK_MISMATCH, BLOCK_FROM_THE_FUTURE, DUPLICATE_BLOCK, SPLIT, OLD_BLOCK_NOT_FOUND, INVALID_TIMESTAMP,
        MISSING_BLOCKCHAIN_DEPENDENCY, INVALID_ROOT_HASH, INVALID_EXTRA_DATA, INVALID_PRIMARY
    }
}

/**
 * Just a [String] wrapper that signals the string is actually a classpath
 */
data class DynamicClassName(val className: String) {


    companion object {

        @JvmStatic
        fun build(className: String?): DynamicClassName? {

            return if (className == null) {
                null
            } else {
                // Maybe verify structure here? Remember that we have "ebft" as a shortcut
                DynamicClassName(className)
            }

        }

        @JvmStatic
        fun buildList(classNames: List<String>): List<DynamicClassName> {
            val retList = ArrayList<DynamicClassName>()
            for (name in classNames) {
                val wrapped = build(name)
                if (wrapped != null) {
                    retList.add(wrapped)
                }
            }
            return retList
        }
    }
}
