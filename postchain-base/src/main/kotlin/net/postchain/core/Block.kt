// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.crypto.Signature

interface BlockHeader {
    val prevBlockRID: ByteArray
    val rawData: ByteArray
    val blockRID: ByteArray // it's not a part of header but derived from it
}

open class BlockData(
        val header: BlockHeader,
        val transactions: List<ByteArray>
)

/**
 * BlockDetail returns a more in deep block overview
 * ATM it is mainly used to reply to explorer's queries
 */
open class TxDetail(
        val rid: ByteArray,
        val hash: ByteArray,
        val data: ByteArray?
)

open class BlockDetail(
        val rid: ByteArray,
        val prevBlockRID: ByteArray,
        val header: ByteArray,
        val height: Long,
        val transactions: List<TxDetail>,
        val witness: ByteArray,
        val timestamp: Long)

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
        MISSING_BLOCKCHAIN_DEPENDENCY, INVALID_ROOT_HASH }
}

/**
 * Witness is a generalization over signatures.
 * Block-level witness is something which proves that block is valid and properly authorized.
 */
interface BlockWitness {
    //    val blockRID: ByteArray
    fun getRawData(): ByteArray
}

open class BlockDataWithWitness(header: BlockHeader, transactions: List<ByteArray>, val witness: BlockWitness)
    : BlockData(header, transactions)

interface MultiSigBlockWitness : BlockWitness {
    fun getSignatures(): Array<Signature>
}

/**
 * This is a DTO we will use to build a block.
 * Note that we don't hold the RID of the block itself, b/c we don't know it yet.
 *
 * @property blockHeightDependencyArr holds all the Block RIDs of the last block of all this blockchain's dependencies.
 *           ("null" means this blockchain doesn't have any dependencies)
 */
class InitialBlockData(
        val blockchainRid: BlockchainRid,
        val blockIID: Long,
        val chainID: Long,
        val prevBlockRID: ByteArray,
        val height: Long,
        val timestamp: Long,
        val blockHeightDependencyArr: Array<ByteArray?>?)


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
