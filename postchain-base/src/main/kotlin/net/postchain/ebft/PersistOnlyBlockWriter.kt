package net.postchain.ebft

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.BaseBlockEContext
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockStore
import net.postchain.core.block.BlockTrace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PersistOnlyBlockWriter(
        private val loggingContext: Map<String, String>,
        private val nodeIndex: Int,
        private val chainId: Long,
        private val blockchainRid: BlockchainRid,
        private val blockStore: BlockStore,
        private val storage: Storage,
        private val transactionFactory: TransactionFactory
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
                    blockStore.commitBlock(bctx, block.witness)
                    logger.info("Saved block: height: ${initialBlockData.height}, block-rid: ${block.header.blockRID.toHex()}, prev-block-rid: ${block.header.prevBlockRID.toHex()}")
                    true
                }
            }
        }, executor)
    }
}