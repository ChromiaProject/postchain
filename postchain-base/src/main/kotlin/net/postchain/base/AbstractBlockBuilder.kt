// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.base.data.GenericBlockHeaderValidator
import net.postchain.common.TimeLog
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.core.ValidationResult.Result.OK
import net.postchain.core.ValidationResult.Result.PREV_BLOCK_MISMATCH
import net.postchain.debug.BlockTrace

/**
 * The abstract block builder has only a vague concept about the core procedures of a block builder, for example:
 * - to only append correct transactions to a block, and
 * - that a block must be validated and finalized when it's done, and
 * - can call functions on the [BlockWitnessManager] but cannot create a [BlockWitnessManager]
 * etc
 *
 * Everything else is left to sub-classes.
 */
abstract class AbstractBlockBuilder(
        val ectx: EContext,               // a general DB context (use bctx when possible)
        val blockchainRID: BlockchainRid, // is the RID of the chain
        val store: BlockStore,
        val txFactory: TransactionFactory // Used for serializing transaction data
) : BlockBuilder, TxEventSink {

    companion object: KLogging()

    // ----------------------------------
    // functions which need to be implemented in a concrete BlockBuilder:
    // ----------------------------------
    abstract protected val blockWitnessManager: BlockWitnessManager
    abstract protected fun computeMerkleRootHash(): ByteArray              // Computes the root hash for the Merkle tree of transactions currently in a block
    abstract protected fun makeBlockHeader(): BlockHeader                  // Create block header from initial block data
    abstract protected fun buildBlockchainDependencies(partialBlockHeader: BlockHeader?): BlockchainDependencies

    // ----------------------------------
    // Public b/c need to be accessed by subclasses
    // ----------------------------------
    var finalized: Boolean = false                   // signalling if further updates to block is permitted
    val rawTransactions = mutableListOf<ByteArray>() // list of encoded transactions
    val transactions = mutableListOf<Transaction>()  // list of decoded transactions
    var blockchainDependencies: BlockchainDependencies? = null //  is the dependencies the configuration tells us to use
    lateinit var bctx: BlockEContext                 // is the context for this specific block
    lateinit var initialBlockData: InitialBlockData  //
    var _blockData: BlockData? = null                // complete set of data for the block including header and [rawTransactions]
    var buildingNewBlock: Boolean = false            // remains "false" as long we got a block from some other node

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
     * (Note: don't call this. We only keep this as a public function for legacy tests to work)
     */
    fun validateBlockHeader(blockHeader: BlockHeader): ValidationResult {
        val nrOfDependencies = blockchainDependencies?.all()?.size ?: 0
        return GenericBlockHeaderValidator.advancedValidateAgainstKnownBlocks(
            blockHeader,
            initialBlockData,
            ::computeMerkleRootHash,
            ::getBlockRidAtHeight,
            bctx.timestamp,
            nrOfDependencies
        )
    }


    /**
     * @return the block RID at a certain height
     */
    fun getBlockRidAtHeight(height: Long) = store.getBlockRID(ectx, height)

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
        val witnessBuilder = blockWitnessManager.createWitnessBuilderWithoutOwnSignature(_blockData!!.header)
        if (!blockWitnessManager.validateWitness(blockWitness, witnessBuilder)) {
            throw ProgrammerMistake("Invalid witness")
        }
        store.commitBlock(bctx, blockWitness)
        bctx.blockWasCommitted()
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
    override fun setBTrace(newBlockTrace: BlockTrace) {
        if (blockTrace == null) {
            blockTrace = newBlockTrace
        } else {
            // Update existing object with missing info
            blockTrace!!.addDataIfMissing(newBlockTrace)
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
