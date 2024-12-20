// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core.block

import net.postchain.base.ConfirmationProof
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.Shutdownable
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.crypto.PubKey
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import java.util.concurrent.CompletionStage

interface BlockWitnessBuilder {
    fun isComplete(): Boolean
    fun getWitness(): BlockWitness // throws when not complete
    val threshold: Int // Minimum amount of signatures necessary for witness to be valid
}

interface MultiSigBlockWitnessBuilder : BlockWitnessBuilder {
    fun getMySignature(): Signature
    fun applySignature(s: Signature)
}

/**
 * A collection of methods for various blockchain related queries
 */
interface BlockQueries : Shutdownable {
    fun getBlockSignature(blockRID: ByteArray): CompletionStage<Signature>
    fun getLastBlockHeight(): CompletionStage<Long>
    fun getLastBlockTimestamp(): CompletionStage<Long>
    fun getBlockRid(height: Long): CompletionStage<ByteArray?>
    fun getBlockAtHeight(height: Long, includeTransactions: Boolean = true): CompletionStage<BlockDataWithWitness?>
    fun getConfirmationProof(txRID: ByteArray): CompletionStage<ConfirmationProof?>
    fun getBlocksBetweenTimes(timeFilter: BlockQueryTimeFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): CompletionStage<BlockDetailsTruncated>
    fun getBlocksBetweenHeights(heightFilter: BlockQueryHeightFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): CompletionStage<BlockDetailsTruncated>
    fun getBlocksFromHeight(fromHeight: Long, limit: Int): CompletionStage<List<DatabaseAccess.BlockInfoExt>>
    fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): CompletionStage<BlockDetail?>

    fun getBlockTransactionRids(blockRID: ByteArray): CompletionStage<List<ByteArray>>
    fun getTransaction(txRID: ByteArray): CompletionStage<Transaction?>
    fun getTransactionRawData(txRID: ByteArray): CompletionStage<ByteArray?>
    fun getTransactionInfo(txRID: ByteArray): CompletionStage<TransactionInfoExt?>
    fun getTransactionsInfo(timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): CompletionStage<TransactionInfoExtsTruncated>
    fun getTransactionsInfoBySigner(timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): CompletionStage<TransactionInfoExtsTruncated>
    fun getLastTransactionNumber(): CompletionStage<Long>
    fun query(name: String, args: Gtv): CompletionStage<Gtv>
    fun isTransactionConfirmed(txRID: ByteArray): CompletionStage<Boolean>
}

/**
 * Builds one block, either:
 *  1. a block we define ourselves (we are the Primary Node = block builder) or
 *  2. an externally produced block (we loading the finished block from the Primary Node).
 *
 * The life cycle of the [BlockBuilder] is:
 * 1. begin()
 * 2. appendTransaction() <- once per TX
 * 3. finalizeBlock()
 * 4. getBlockWitnessBuilder() <- Applies signatures
 * 5. getBlockData()
 *
 * (For more documentation, see sub classes)
 */
interface BlockBuilder {
    fun begin(partialBlockHeader: BlockHeader?)
    fun appendTransaction(tx: Transaction)
    fun finalizeBlock(): BlockHeader = finalizeBlock(System.currentTimeMillis())
    fun finalizeBlock(timestamp: Long = System.currentTimeMillis()): BlockHeader
    fun finalizeAndValidate(blockHeader: BlockHeader, skipValidationFields: Set<String> = emptySet())
    fun getBlockData(): BlockData
    fun getBlockWitnessBuilder(): BlockWitnessBuilder?
    fun commit(blockWitness: BlockWitness)

    val height: Long?

    // Just debug
    fun getBTrace(): BlockTrace? // Use this function to get quick debug info about the block, note: ONLY for logging!
    fun setBTrace(bTrace: BlockTrace)
}

/**
 * A block builder which automatically manages the connection.
 *
 * Despite its name, it is not related to managed mode.
 */
interface ManagedBlockBuilder : BlockBuilder {
    fun maybeAppendTransaction(tx: Transaction): Exception?
    fun rollback()
}

/**
 * Strategy configurations for how to create new blocks
 */
interface BlockBuildingStrategy {
    fun preemptiveBlockBuilding(): Boolean
    fun shouldBuildPreemptiveBlock(): Boolean
    fun shouldBuildBlock(): Boolean
    fun shouldForceStopBlockBuilding(): Boolean
    fun setForceStopBlockBuilding(value: Boolean)
    fun hasReachedTimeConstraintsForBlockBuilding(haveSeenTxs: Boolean): Boolean
    fun mustWaitMinimumBuildBlockTime(): Long
    fun mustWaitBeforeBuildBlock(): Boolean
    fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean
    fun blockCommitted(blockData: BlockData)
    fun blockFailed()
}

interface SpecialTxHandlerAware {
    var specialTxHandler: SpecialTransactionHandler?
}
