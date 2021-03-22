// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.TimeLog
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.core.ValidationResult.Result.OK
import net.postchain.core.ValidationResult.Result.PREV_BLOCK_MISMATCH

/**
 * This class includes the bare minimum functionality required by a real block builder
 *
 * @property ectx Connection context including blockchain and node identifiers
 * @property store For database access
 * @property txFactory Used for serializing transaction data
 * @property finalized Boolean signalling if further updates to block is permitted
 * @property rawTransactions list of encoded transactions
 * @property transactions list of decoded transactions
 * @property _blockData complete set of data for the block including header and [rawTransactions]
 * @property initialBlockData
 */
abstract class AbstractBlockBuilder(
        val ectx: EContext,
        val blockchainRID: BlockchainRid,
        val store: BlockStore,
        val txFactory: TransactionFactory
) : BlockBuilder, TxEventSink {

    // functions which need to be implemented in a concrete BlockBuilder:
    abstract fun makeBlockHeader(): BlockHeader
    abstract fun validateBlockHeader(blockHeader: BlockHeader): ValidationResult
    abstract fun validateWitness(blockWitness: BlockWitness): Boolean
    abstract fun buildBlockchainDependencies(partialBlockHeader: BlockHeader?): BlockchainDependencies
    // fun getBlockWitnessBuilder(): BlockWitnessBuilder?;

    var finalized: Boolean = false
    val rawTransactions = mutableListOf<ByteArray>()
    val transactions = mutableListOf<Transaction>()
    var blockchainDependencies: BlockchainDependencies? = null
    lateinit var bctx: BlockEContext
    lateinit var initialBlockData: InitialBlockData
    var _blockData: BlockData? = null
    var buildingNewBlock: Boolean = false

    /**
     * Retrieve initial block data and set block context
     *
     * @param partialBlockHeader might hold the header.
     */
    override fun begin(partialBlockHeader: BlockHeader?) {
        if (::initialBlockData.isInitialized) {
            ProgrammerMistake("Attempted to begin block second time")
        }
        blockchainDependencies = buildBlockchainDependencies(partialBlockHeader)
        initialBlockData = store.beginBlock(ectx, blockchainRID, blockchainDependencies!!.extractBlockHeightDependencyArray())
        bctx = BaseBlockEContext(
                ectx,
                initialBlockData.height,
                initialBlockData.blockIID,
                initialBlockData.timestamp,
                blockchainDependencies!!.extractChainIdToHeightMap(),
                this
        )
        buildingNewBlock = partialBlockHeader != null
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
            throw UserMistake("Transaction ${tx.getRID().toHex()} is not correct")
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
        if (tx.apply(txctx)) {
            txctx.done()
            transactions.add(tx)
            rawTransactions.add(tx.getRawData())
        } else {
            throw UserMistake("Transaction ${tx.getRID().toHex()} failed")
        }
        TimeLog.end("AbstractBlockBuilder.appendTransaction().apply")
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
     * Apart from finalizing the block, validate the header
     *
     * @param blockHeader Block header to finalize and validate
     * @throws UserMistake Happens if validation of the block header fails
     */
    override fun finalizeAndValidate(blockHeader: BlockHeader) {
        val validationResult = validateBlockHeader(blockHeader)
        when (validationResult.result) {
            OK -> {
                store.finalizeBlock(bctx, blockHeader)
                _blockData = BlockData(blockHeader, rawTransactions)
                finalized = true
            }
            PREV_BLOCK_MISMATCH -> throw BadDataMistake(BadDataType.PREV_BLOCK_MISMATCH, validationResult.message)
            else -> throw BadDataMistake(BadDataType.BAD_BLOCK, validationResult.message)
        }
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
     * @param blockWitness The witness for the block
     * @throws ProgrammerMistake If the witness is invalid
     */
    override fun commit(blockWitness: BlockWitness) {
        if (!validateWitness(blockWitness)) {
            throw ProgrammerMistake("Invalid witness")
        }
        store.commitBlock(bctx, blockWitness)
    }
}
