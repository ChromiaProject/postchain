package net.postchain.ebft

import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.core.BlockchainEngine
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PersistOnlyBlockWriter(
        private val loggingContext: Map<String, String>,
        private val nodeIndex: Int,
        private val engine: BlockchainEngine,
) : BlockWriter {

    companion object : KLogging()

    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "$nodeIndex-c${engine.chainID}-PersistOnlyBlockWriter")
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

                val (theBlockBuilder, exception) = engine.persistBlock(block)
                if (exception != null) {
                    throw exception
                } else {
                    try {
                        theBlockBuilder.commit(block.witness)
                        logger.info("Saved block: height: ${theBlockBuilder.height}, block-rid: ${block.header.blockRID.toHex()}, prev-block-rid: ${block.header.prevBlockRID.toHex()}")
                    } catch (e: Exception) {
                        // In case exception was thrown before DB commit was successful we need to roll back
                        theBlockBuilder.rollback()
                        throw e
                    }
                }
            }
        }, executor)
    }
}