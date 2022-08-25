// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.TimeLog
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.TransactionFailed
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.core.TransactionFactory
import net.postchain.core.block.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The abstract block builder has only a vague concept about the core procedures of a block builder, for example:
 * - to only append correct transactions to a block, and
 * - that a block must be validated and finalized when it's done, and
 * - can call functions on the [BlockWitnessProvider] but cannot create a [BlockWitnessProvider]
 * etc
 *
 * Everything else is left to sub-classes.
 */
abstract class AbstractBlockBuilder(
        val ectx: EContext,               // a general DB context (use bctx when possible)
        val blockchainRID: BlockchainRid, // is the RID of the chain
        val store: BlockStore,
        val txFactory: TransactionFactory, // Used for serializing transaction data
        private val maxTxExecutionTime: Long
) : BlockBuilder, TxEventSink {

    companion object: KLogging()

    // ----------------------------------
    // functions which need to be implemented in a concrete BlockBuilder:
    // ----------------------------------
    abstract protected val blockWitnessProvider: BlockWitnessProvider
    abstract protected fun computeMerkleRootHash(): ByteArray              // Computes the root hash for the Merkle tree of transactions currently in a block
    abstract protected fun makeBlockHeader(): BlockHeader                  // Create block header from initial block data
    abstract protected fun buildBlockchainDependencies(partialBlockHeader: BlockHeader?): BlockchainDependencies

    // ----------------------------------
    // Public b/c need to be accessed by subclasses
    // ----------------------------------
    protected var finalized: Boolean = false                   // signalling if further updates to block is permitted
    protected val rawTransactions = mutableListOf<ByteArray>() // list of encoded transactions
    val transactions = mutableListOf<Transaction>()  // list of decoded transactions
    protected var blockchainDependencies: BlockchainDependencies? = null //  is the dependencies the configuration tells us to use
    lateinit var bctx: BlockEContext                 // is the context for this specific block
    internal lateinit var initialBlockData: InitialBlockData  //
    protected var _blockData: BlockData? = null                // complete set of data for the block including header and [rawTransactions]
    protected var buildingNewBlock: Boolean = false            // remains "false" as long we got a block from some other node

    var blockTrace: BlockTrace? = null               // Only for logging, remains "null" unless TRACE

    /**
     * Retrieve initial block data and set block context
     *
     * @param partialBlockHeader might hold the header.
     */
    override fun begin(partialBlockHeader: BlockHeader?) {
        beginLog("Begin")
        if (::initialBlockData.isInitialized) {
            ProgrammerMistake("Attempted to begin block second time")
        }
        blockchainDependencies = buildBlockchainDependencies(partialBlockHeader)
        initialBlockData =
            store.beginBlock(ectx, blockchainRID, blockchainDependencies!!.extractBlockHeightDependencyArray())
        bctx = BaseBlockEContext(
            ectx,
            initialBlockData.height,
            initialBlockData.blockIID,
            initialBlockData.timestamp,
            blockchainDependencies!!.extractChainIdToHeightMap(),
            this
        )
        buildingNewBlock = partialBlockHeader == null // If we have a header this must be an old block we are loading
        beginLog("End")
    }

    /**
     * Apply transaction to current working block
     *
     * @param tx transaction to be added to block
     * @throws ProgrammerMistake if block is finalized
     * @throws UserMistake transaction is not correct
     * @throws UserMistake failed to save transaction to database
     * @throws UserMistake failed to apply transaction and update database state
     */
    override fun appendTransaction(tx: Transaction) {
        if (finalized) throw ProgrammerMistake("Block is already finalized")
        // tx.isCorrect may also throw UserMistake to provide
        // a meaningful error message to log.
        TimeLog.startSum("AbstractBlockBuilder.appendTransaction().isCorrect")
        if (!tx.isCorrect()) {
            throw TransactionIncorrect("Transaction ${tx.getRID().toHex()} is not correct")
        }
        TimeLog.end("AbstractBlockBuilder.appendTransaction().isCorrect")
        val txctx: TxEContext
        try {
            TimeLog.startSum("AbstractBlockBuilder.appendTransaction().addTransaction")
            txctx = store.addTransaction(bctx, tx)
            TimeLog.end("AbstractBlockBuilder.appendTransaction().addTransaction")
        } catch (e: Exception) {
            throw UserMistake("Failed to save tx to database", e)
        }
        // In case of errors, tx.apply may either return false or throw UserMistake
        TimeLog.startSum("AbstractBlockBuilder.appendTransaction().apply")

        if (applyTransaction(tx, txctx)) {
            txctx.done()
            transactions.add(tx)
            rawTransactions.add(tx.getRawData())
        } else {
            throw TransactionFailed("Transaction ${tx.getRID().toHex()} failed")
        }
        TimeLog.end("AbstractBlockBuilder.appendTransaction().apply")
    }

    private fun applyTransaction(tx: Transaction, txctx: TxEContext): Boolean {
        return if (maxTxExecutionTime > 0) {
            try {
                CompletableFuture.supplyAsync { tx.apply(txctx) }
                        .orTimeout(maxTxExecutionTime, TimeUnit.MILLISECONDS)
                        .get()
            } catch (e: ExecutionException) {
                throw if (e.cause is TimeoutException) {
                    TransactionFailed("Transaction failed to execute within given time constraint: $maxTxExecutionTime ms")
                } else {
                    e
                }
            }
        } else {
            tx.apply(txctx)
        }
    }

    /**
     * By finalizing the block we won't allow any more transactions to be added, and the block RID and timestamp are set
     */
    override fun finalizeBlock(): BlockHeader {
        val blockHeader = makeBlockHeader()
        store.finalizeBlock(bctx, blockHeader)
        _blockData = BlockData(blockHeader, rawTransactions)
        finalized = true
        return blockHeader
    }

    /**
     * Return block data if block is finalized.
     *
     * @throws ProgrammerMistake When block is not finalized
     */
    override fun getBlockData(): BlockData {
        return _blockData ?: throw ProgrammerMistake("Block is not finalized yet")
    }

    /**
     * By committing to the block we update the database to include the witness for that block
     *
     * Note: replicas commit blocks too, so we cannot append our own signature on the [BlockWitnessBuilder].
     *
     * @param blockWitness The witness for the block
     * @throws ProgrammerMistake If the witness is invalid
     */
    override fun commit(blockWitness: BlockWitness) {
        commitLog("Begin")
        val witnessBuilder = blockWitnessProvider.createWitnessBuilderWithoutOwnSignature(_blockData!!.header)
        if (!blockWitnessProvider.validateWitness(blockWitness, witnessBuilder)) {
            throw ProgrammerMistake("Invalid witness")
        }
        store.commitBlock(bctx, blockWitness)
        commitLog("End")
    }

    // -----------------
    // Logging boilerplate
    // -----------------

    // Use this function to get quick debug info about the block, note ONLY for logging!
    override fun getBTrace(): BlockTrace? {
        return blockTrace
    }

    // Only used for logging
    override fun setBTrace(bTrace: BlockTrace) {
        if (blockTrace == null) {
            blockTrace = bTrace
        } else {
            // Update existing object with missing info
            blockTrace!!.addDataIfMissing(bTrace)
        }
    }

    private fun beginLog(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("${ectx.chainID} begin() -- $str, from block: ${getBTrace()}")
        }
    }

    private fun commitLog(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("${ectx.chainID} commit() -- $str, from block: ${getBTrace()}")
        }
    }
}

