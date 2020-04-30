// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A wrapper class for the [engine] and [blockQueries], starting new threads when running
 */
class BaseBlockDatabase(
        private val engine: BlockchainEngine,
        private val blockQueries: BlockQueries,
        val nodeIndex: Int
) : BlockDatabase {

    private val executor = Executors.newSingleThreadExecutor {
        Thread(it, "$nodeIndex-BaseBlockDatabaseWorker")
                .apply {
                    isDaemon = true // So it can't block the JVM from exiting if still running
                }
    }
    private var blockBuilder: ManagedBlockBuilder? = null
    private var witnessBuilder: MultiSigBlockWitnessBuilder? = null

    companion object : KLogging()

    fun stop() {
        logger.debug("BaseBlockDatabase $nodeIndex stopping")
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS) // TODO: [et]: 1000 ms
        maybeRollback()
    }

    private fun <RT> runOp(name: String, op: () -> RT): Promise<RT, Exception> {
        logger.trace("BaseBlockDatabase $nodeIndex putting a job")

        val deferred = deferred<RT, Exception>()
        executor.execute {
            try {
                logger.debug("Starting job $name")
                val res = op()
                logger.debug("Finish job $name")
                deferred.resolve(res)
            } catch (e: Exception) {
                logger.debug("Failed job $name", e)
                deferred.reject(e)
            }
        }

        return deferred.promise
    }

    private fun maybeRollback() {
        logger.trace("BaseBlockDatabase $nodeIndex maybeRollback.")
        if (blockBuilder != null) {
            logger.debug("BaseBlockDatabase $nodeIndex blockBuilder is not null.")
        }
        blockBuilder?.rollback()
        blockBuilder = null
        witnessBuilder = null
    }

    override fun addBlock(block: BlockDataWithWitness): Promise<Unit, Exception> {
        return runOp("addBlock ${block.header.blockRID.toHex()}") {
            maybeRollback()
            val (theBlockBuilder, exception) = engine.loadUnfinishedBlock(block)
            if (exception != null) {
                theBlockBuilder.rollback()
                throw UserMistake("Can't add block", exception)
            } else {
                theBlockBuilder.commit(block.witness)
            }
        }
    }

    override fun loadUnfinishedBlock(block: BlockData): Promise<Signature, Exception> {
        return runOp("loadUnfinishedBlock ${block.header.blockRID.toHex()}") {
            maybeRollback()
            val (theBlockBuilder, exception) = engine.loadUnfinishedBlock(block)
            if (exception != null) {
                theBlockBuilder.rollback()
                throw UserMistake("Can't load unfinished block", exception)
            } else {
                blockBuilder = theBlockBuilder
                witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
                witnessBuilder!!.getMySignature()
            }
        }
    }

    override fun commitBlock(signatures: Array<Signature?>): Promise<Unit, Exception> {
        return runOp("commitBlock") {
            // TODO: process signatures
            blockBuilder!!.commit(witnessBuilder!!.getWitness())
            blockBuilder = null
            witnessBuilder = null
        }
    }

    override fun buildBlock(): Promise<Pair<BlockData, Signature>, Exception> {
        return runOp("buildBlock") {
            maybeRollback()
            val (theBlockBuilder, exception) = engine.buildBlock()
            if (exception != null) {
                theBlockBuilder.rollback()
                throw UserMistake("Can't build block", exception)
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
                logger.debug("Signature invalid", e)
                false
            }
        } else {
            false
        }
    }

    override fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception> {
        return blockQueries.getBlockSignature(blockRID)
    }

    override fun getBlockAtHeight(height: Long): Promise<BlockDataWithWitness, Exception> {
        return blockQueries.getBlockAtHeight(height)
    }

}