package net.postchain.ebft

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseBlockHeader
import net.postchain.base.data.GenericBlockHeaderValidator
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BadBlockException
import net.postchain.core.BeforeCommitHandler
import net.postchain.core.BlockRid
import net.postchain.core.ConfigurationMismatchException
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.ValidationResult
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockStore
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.InitialBlockData
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PersistOnlyBlockWriter(
        private val loggingContext: Map<String, String>,
        private val nodeIndex: Int,
        private val chainId: Long,
        private val blockchainRid: BlockchainRid,
        private val configHash: ByteArray,
        private val blockStore: BlockStore,
        private val storage: Storage,
        private val transactionFactory: TransactionFactory,
        private val beforeCommitHandler: BeforeCommitHandler,
        private val afterCommitHandler: AfterCommitHandler
) : BlockWriter {

    companion object : KLogging()

    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "$nodeIndex-c$chainId-PersistOnlyBlockWriter")
    }

    override fun addBlock(block: BlockDataWithWitness, dependsOn: CompletableFuture<Unit>?, existingBTrace: BlockTrace?): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync({
            withLoggingContext(loggingContext) {
                if (dependsOn != null) {
                    if (dependsOn.isCompletedExceptionally) {
                        throw BDBAbortException(block)
                    }
                    if (!dependsOn.isDone) {
                        // If we get here the caller must have sent the incorrect future.
                        throw ProgrammerMistake("Previous completion is unfinished ${dependsOn.isDone}")
                    }
                }

                withWriteConnection(storage, chainId) { ctx ->
                    val initialBlockData = blockStore.beginBlock(ctx, blockchainRid, null)
                    var nextTransactionNumber = blockStore.getLastTransactionNumber(ctx) + 1

                    validateBlockHeader(block, initialBlockData) { height -> blockStore.getBlockRID(ctx, height) }

                    val bctx = BaseBlockEContext(
                            ctx,
                            initialBlockData.height,
                            initialBlockData.blockIID,
                            initialBlockData.timestamp,
                            mapOf(),
                            { _, _, _  -> }
                    )
                    block.transactions.forEach {
                        val decodedTx = transactionFactory.decodeTransaction(it)
                        blockStore.addTransaction(bctx, decodedTx, nextTransactionNumber)
                        nextTransactionNumber++
                    }
                    blockStore.finalizeBlock(bctx, block.header)
                    val bTrace = BlockTrace.build(block.header.blockRID, initialBlockData.height)

                    beforeCommitHandler(bTrace, bctx)
                    blockStore.commitBlock(bctx, block.witness)
                    afterCommitHandler(bTrace, bctx.height, bctx.timestamp)

                    logger.info("Saved block: height: ${initialBlockData.height}, block-rid: ${block.header.blockRID.toHex()}, prev-block-rid: ${block.header.prevBlockRID.toHex()}")
                    true
                }
            }
        }, executor)
    }

    private fun validateBlockHeader(block: BlockDataWithWitness, initialBlockData: InitialBlockData, blockRidFromHeight: (height: Long) -> ByteArray?) {
        // Odd to have to cast, but we do that in normal validation as well
        val header = block.header as BaseBlockHeader
        // We can't do advanced validation here because we don't apply txs
        val validationResult = GenericBlockHeaderValidator.basicValidationAgainstKnownBlocks(
                BlockRid(header.blockRID),
                BlockRid(header.prevBlockRID),
                header.blockHeaderRec.getHeight(),
                BlockRid(initialBlockData.prevBlockRID),
                initialBlockData.height,
                blockRidFromHeight
        )

        if (validationResult.result != ValidationResult.Result.OK) {
            throw BadBlockException(validationResult.message)
        }

        // Witness is already checked by synchronizer, unless config is not currently loaded, so we need to check that
        val blockConfigHash = header.extraData[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()
        if (blockConfigHash != null && !blockConfigHash.contentEquals(configHash)) {
            throw ConfigurationMismatchException("Block configuration hash ${blockConfigHash.toHex()} does not match currently loaded configuration hash ${configHash.toHex()}")
        }
    }
}