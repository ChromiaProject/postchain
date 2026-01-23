package net.postchain.base.data

import net.postchain.base.AbstractBlockBuilder
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.BlockchainDependencies
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.core.BadBlockException
import net.postchain.core.BlockRid
import net.postchain.core.ConfigurationMismatchException
import net.postchain.core.EContext
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.core.ValidationResult
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockStore
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.gtv.Gtv

class PersistOnlyBlockBuilder(
        ectx: EContext,
        blockchainRID: BlockchainRid,
        store: BlockStore,
        override val blockWitnessProvider: BlockWitnessProvider,
        private val configHash: ByteArray
) : AbstractBlockBuilder(ectx, blockchainRID, store, true) {

    override fun computeMerkleRootHash(): ByteArray {
        // We could support this but we don't have to
        throw ProgrammerMistake("You can't call computeMerkleRootHash on a persist only block builder")
    }

    override fun makeBlockHeader(timestamp: Long): BlockHeader {
        throw ProgrammerMistake("You can't call makeBlockHeader on a persist only block builder")
    }

    override fun buildBlockchainDependencies(partialBlockHeader: BlockHeader?): BlockchainDependencies =
            BlockchainDependencies(arrayOf()) // We won't be using blockchain dependencies

    override fun appendTransaction(tx: Transaction) {
        DatabaseAccess.of(bctx).insertTransaction(bctx, tx, nextTransactionNumber)
        nextTransactionNumber++
        transactions.add(tx)
        rawTransactions.add(tx.getRawData())
    }

    override fun finalizeBlock(timestamp: Long): BlockHeader {
        throw ProgrammerMistake("You can't finalize a persist only block builder")
    }

    override fun finalizeAndValidate(blockHeader: BlockHeader, skipValidationFields: Set<String>, skipRootHashValidation: Boolean) {
        val header = blockHeader as BaseBlockHeader
        // We can't do advanced validation here because we don't apply txs
        val validationResult = GenericBlockHeaderValidator.basicValidationAgainstKnownBlocks(
                BlockRid(header.blockRID),
                BlockRid(header.prevBlockRID),
                header.blockHeaderRec.getHeight(),
                BlockRid(initialBlockData.prevBlockRID),
                initialBlockData.height,
                { height -> store.getBlockRID(bctx, height) }
        )

        if (validationResult.result != ValidationResult.Result.OK) {
            throw BadBlockException(validationResult.message)
        }

        // Ensure we have correct config loaded
        val blockConfigHash = header.extraData[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()
        if (blockConfigHash != null && !blockConfigHash.contentEquals(configHash)) {
            throw ConfigurationMismatchException("Block configuration hash ${blockConfigHash.toHex()} does not match currently loaded configuration hash ${configHash.toHex()}")
        }

        store.finalizeBlock(bctx, blockHeader)
        _blockData = BlockData(blockHeader, rawTransactions)
        finalized = true
    }

    override fun getBlockWitnessBuilder(): BlockWitnessBuilder {
        if (!finalized) {
            throw ProgrammerMistake("Block is not finalized yet.")
        }

        return blockWitnessProvider.createWitnessBuilderWithOwnSignature(_blockData!!.header)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {} // We won't get any events
}
