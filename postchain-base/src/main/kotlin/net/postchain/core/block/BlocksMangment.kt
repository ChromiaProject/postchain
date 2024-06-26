// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core.block

import net.postchain.base.ConfirmationProof
import net.postchain.base.ConfirmationProofMaterial
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.Shutdownable
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TxEContext
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

interface BlockStore {
    fun beginBlock(ctx: EContext, blockchainRID: BlockchainRid, blockHeightDependencies: Array<Hash?>?): InitialBlockData
    fun addTransaction(bctx: BlockEContext, tx: Transaction, transactionNumber: Long): TxEContext
    fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader)
    fun commitBlock(bctx: BlockEContext, w: BlockWitness)
    fun getBlockHeightFromOwnBlockchain(ctx: EContext, blockRID: ByteArray): Long? // returns null if not found
    fun getBlockHeightFromAnyBlockchain(ctx: EContext, blockRID: ByteArray, chainId: Long): Long? // returns null if not found
    fun getChainId(ctx: EContext, blockchainRID: BlockchainRid): Long? // returns null if not found
    fun getBlockRID(ctx: EContext, height: Long): ByteArray? // returns null if height is out of range
    fun getLastBlockHeight(ctx: EContext): Long // height of the last block, first block has height 0
    fun getLastBlockTimestamp(ctx: EContext): Long
    fun getBlockHeightInfo(ctx: EContext, blockchainRID: BlockchainRid): Pair<Long, Hash>?

    //    fun getBlockData(ctx: EContext, blockRID: ByteArray): BlockData
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray

    fun getBlocks(ctx: EContext, beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail>
    fun getBlocksBeforeHeight(ctx: EContext, beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail>
    fun getBlocksFromHeight(ctx: EContext, fromHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail>
    fun getBlock(ctx: EContext, blockRID: ByteArray, txHashesOnly: Boolean): BlockDetail?
    fun getTransactionInfo(ctx: EContext, txRID: ByteArray): TransactionInfoExt?
    fun getTransactionsInfo(ctx: EContext, beforeTime: Long, limit: Int): List<TransactionInfoExt>
    fun getTransactionsInfoBySigner(ctx: EContext, beforeTime: Long, limit: Int, signer: PubKey): List<TransactionInfoExt>
    fun getLastTransactionNumber(ctx: EContext): Long
    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray?
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): List<ByteArray>

    fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean
    fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): ConfirmationProofMaterial?
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
    fun getBlockHeader(blockRID: ByteArray): CompletionStage<BlockHeader>
    fun getConfirmationProof(txRID: ByteArray): CompletionStage<ConfirmationProof?>
    fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): CompletionStage<List<BlockDetail>>
    fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): CompletionStage<List<BlockDetail>>
    fun getBlocksFromHeight(fromHeight: Long, limit: Int, txHashesOnly: Boolean): CompletionStage<List<BlockDetail>>
    fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): CompletionStage<BlockDetail?>

    fun getBlockTransactionRids(blockRID: ByteArray): CompletionStage<List<ByteArray>>
    fun getTransaction(txRID: ByteArray): CompletionStage<Transaction?>
    fun getTransactionRawData(txRID: ByteArray): CompletionStage<ByteArray?>
    fun getTransactionInfo(txRID: ByteArray): CompletionStage<TransactionInfoExt?>
    fun getTransactionsInfo(beforeTime: Long, limit: Int): CompletionStage<List<TransactionInfoExt>>
    fun getTransactionsInfoBySigner(beforeTime: Long, limit: Int, signer: PubKey): CompletionStage<List<TransactionInfoExt>>
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
    fun finalizeAndValidate(blockHeader: BlockHeader)
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
