package net.postchain.core

import net.postchain.base.BlockchainRid


interface BlockHeader {
    val prevBlockRID: ByteArray
    val rawData: ByteArray
    val blockRID: ByteArray // it's not a part of header but derived from it
}

open class BlockData(
        val header: BlockHeader,
        val transactions: List<ByteArray>)


/**
 * BlockDetail returns a more in deep block overview
 * ATM it is mainly used to reply to explorer's queries
 */
open class PartialTx (
    val rid: ByteArray,
    val hash: ByteArray
)
open class BlockDetail(
        val rid: ByteArray,
        val prevBlockRID: ByteArray,
        val header: ByteArray,
        val height: Long,
        val transactions: List<ByteArray>,
        val partialTransaction: List<PartialTx>,
        val witness: ByteArray,
        val timestamp: Long)

data class ValidationResult(
        val result: Boolean,
        val message: String? = null)

/**
 * Witness is a generalization over signatures.
 * Block-level witness is something which proves that block is valid and properly authorized.
 */
interface BlockWitness {
    //    val blockRID: ByteArray
    fun getRawData(): ByteArray
}

open class BlockDataWithWitness(header: BlockHeader, transactions: List<ByteArray>, val witness: BlockWitness?)
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
