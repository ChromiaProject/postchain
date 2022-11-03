// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull

open class TxDetail(
        val rid: ByteArray,
        val hash: ByteArray,
        val data: ByteArray?
) {
    fun toGtv(): Gtv {
        return gtv(mapOf(
                "rid" to gtv(rid),
                "hash" to gtv(hash),
                "data" to (data?.let { gtv(it) } ?: GtvNull) as Gtv
        ))
    }
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

data class ValidationResult(
        val result: Result,
        val message: String = "") {
    enum class Result {
        OK, PREV_BLOCK_MISMATCH, BLOCK_FROM_THE_FUTURE, DUPLICATE_BLOCK, SPLIT, OLD_BLOCK_NOT_FOUND, INVALID_TIMESTAMP,
        MISSING_BLOCKCHAIN_DEPENDENCY, INVALID_ROOT_HASH, INVALID_EXTRA_DATA }
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
