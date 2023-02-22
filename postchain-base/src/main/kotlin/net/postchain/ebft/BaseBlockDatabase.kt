// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockchainEngine
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.ManagedBlockBuilder
import net.postchain.core.block.MultiSigBlockWitnessBuilder
import net.postchain.crypto.Signature
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A wrapper class for the [engine] and [blockQueries], starting new threads when running
 *
 * NOTE: Re threading
 * [ThreadPoolExecutor] will queue up tasks and execute them in the order they were given.
 * We use only one thread, which means we know the previous task was completed before we begin the next.
 */
class BaseBlockDatabase(
        private val engine: BlockchainEngine,
        private val blockQueries: BlockQueries,
        val nodeIndex: Int
) : BlockDatabase {

    // The executor will only execute one thing at a time, in order
    private val executor = ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
    ) { r: Runnable ->
        Thread(r, "$nodeIndex-BaseBlockDatabaseWorker")
                .apply {
                    isDaemon = true // So it can't block the JVM from exiting if still running
                }
    }

    private var blockBuilder: ManagedBlockBuilder? = null
    private var witnessBuilder: MultiSigBlockWitnessBuilder? = null
    private val queuedBlockCount = AtomicInteger(0)

    companion object : KLogging()

    fun stop() {
        logger.debug { "stop() - Begin, node: $nodeIndex" }
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS) // TODO: [et]: 1000 ms
        maybeRollback()
        logger.debug { "stop() - End, node: $nodeIndex" }
    }

    override fun getQueuedBlockCount(): Int {
        return queuedBlockCount.get()
    }

    private fun <RT> runOpAsync(name: String, op: () -> RT): CompletableFuture<RT> {
        if (logger.isTraceEnabled) {
            logger.trace("runOpAsync() - $nodeIndex putting job $name on queue")
        }

        return CompletableFuture.supplyAsync({
            try {
                if (logger.isDebugEnabled) {
                    logger.debug("Starting job $name")
                }
                val res = op()
                if (logger.isDebugEnabled) {
                    logger.debug("Finished job $name")
                }
                res
            } catch (e: Exception) {
                logger.warn(e) { "Failed job $name" }
                throw e
            }
        }, executor)
    }

    private fun maybeRollback() {
        logger.trace { "maybeRollback() node: $nodeIndex." }
        if (blockBuilder != null) {
            logger.debug { "maybeRollback() node: $nodeIndex, blockBuilder is not null." }
            engine.getTransactionQueue().retryAllTakenTransactions()
        }
        blockBuilder?.rollback()
        blockBuilder = null
        witnessBuilder = null
    }

    /**
     * Adding a block is different from building a block. Here we just want to push this (existing) block into the DB.
     *
     * NOTE:
     * The [BlockchainEngine] creates a new [BlockBuilder] instance for each "addBlock()" call,
     * BUT unlike the other methods in this class "addBlock()" doesn't update the blockBuilder member field.
     *
     * This is why there is no use setting the [BlockTrace] for this method, we have to send the bTrace instance
     *
     * @param block to be added
     * @param dependsOn is the future for the previous block (by the time we access this promise it will be "done").
     * @param existingBTrace is the trace data of the block we have at current moment. For production this is "null"
     */
    override fun addBlock(block: BlockDataWithWitness, dependsOn: CompletableFuture<Unit>?,
                          existingBTrace: BlockTrace?): CompletableFuture<Unit> {
        queuedBlockCount.incrementAndGet()
        return runOpAsync("addBlock ${block.header.blockRID.toHex()}") {
            queuedBlockCount.decrementAndGet()
            if (dependsOn != null) {
                if (dependsOn.isCompletedExceptionally) {
                    throw BDBAbortException(block, dependsOn)
                }
                if (!dependsOn.isDone) {
                    // If we get here the caller must have sent the incorrect future.
                    throw ProgrammerMistake("Previous completion is unfinished ${dependsOn.isDone}")
                }
            }
            addBlockLog("Begin")
            maybeRollback()
            val (theBlockBuilder, exception) = engine.loadUnfinishedBlock(block)
            if (exception != null) {
                addBlockLog("Got error when loading: ${exception.message}")
                try {
                    theBlockBuilder.rollback()
                } catch (ignore: Exception) {
                }
                throw exception
            } else {
                updateBTrace(existingBTrace, theBlockBuilder.getBTrace())
                theBlockBuilder.commit(block.witness) // No need to set BTrace, because we have it
                addBlockLog("Done commit", theBlockBuilder.getBTrace())
            }
        }
    }

    override fun loadUnfinishedBlock(block: BlockData): CompletionStage<Signature> {
        return runOpAsync("loadUnfinishedBlock ${block.header.blockRID.toHex()}") {
            maybeRollback()
            val (theBlockBuilder, exception) = engine.loadUnfinishedBlock(block)
            if (exception != null) {
                try {
                    theBlockBuilder.rollback()
                } catch (ignore: Exception) {
                }
                throw exception
            } else {
                blockBuilder = theBlockBuilder
                witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
                witnessBuilder!!.getMySignature()
            }
        }
    }

    override fun commitBlock(signatures: Array<Signature?>): CompletionStage<Unit> {
        return runOpAsync("commitBlock") {
            // TODO: process signatures
            blockBuilder!!.commit(witnessBuilder!!.getWitness())
            blockBuilder = null
            witnessBuilder = null
        }
    }

    override fun buildBlock(): CompletionStage<Pair<BlockData, Signature>> {
        return runOpAsync("buildBlock") {
            maybeRollback()
            val (theBlockBuilder, exception) = engine.buildBlock()
            if (exception != null) {
                try {
                    theBlockBuilder.rollback()
                } catch (ignore: Exception) {
                }
                throw UserMistake("Can't build block: ${exception.message}", exception)
            } else {
                blockBuilder = theBlockBuilder
                witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
                Pair(blockBuilder!!.getBlockData(), witnessBuilder!!.getMySignature())
            }
        }
    }

    override fun verifyBlockSignature(s: Signature): Boolean {
        return if (witnessBuilder != null) {
            try {
                witnessBuilder!!.applySignature(s)
                true
            } catch (e: Exception) {
                logger.debug(e) { "Signature invalid" }
                false
            }
        } else {
            false
        }
    }

    override fun getBlockSignature(blockRID: ByteArray): CompletionStage<Signature> {
        return blockQueries.getBlockSignature(blockRID)
    }

    override fun getBlockAtHeight(height: Long, includeTransactions: Boolean): CompletionStage<BlockDataWithWitness?> {
        return blockQueries.getBlockAtHeight(height, includeTransactions)
    }

    // -----------
    // Only for logging
    // -----------

    override fun setBlockTrace(blockTrace: BlockTrace) {
        if (this.blockBuilder != null) {
            if (this.blockBuilder!!.getBTrace() != null) {
                this.blockBuilder!!.getBTrace()!!.addDataIfMissing(blockTrace)
            } else {
                this.blockBuilder!!.setBTrace(blockTrace) // use the one we got
            }
        }
    }

    fun updateBTrace(existingBTrace: BlockTrace?, newBlockTrace: BlockTrace?) {
        addBlockLog("About to commit", newBlockTrace)
        if (existingBTrace != null) {
            if (newBlockTrace != null) {
                existingBTrace.blockRid = newBlockTrace.blockRid // Our old BTrace doesn't have the block RID
                newBlockTrace.addDataIfMissing(existingBTrace) // Overwrite if it doesn't exist
            } else {
                addBlockLog("ERROR why no BTrace?")
            }
        }
    }

    fun addBlockLog(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("addBlock() -- $str")
        }
    }

    fun addBlockLog(str: String, bTrace: BlockTrace?) {
        if (logger.isTraceEnabled) {
            logger.trace("addBlock() -- $str, from block: $bTrace")
        }
    }
}