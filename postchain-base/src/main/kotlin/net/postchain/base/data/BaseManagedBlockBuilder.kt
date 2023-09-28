// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.data.SqlUtils.isClosed
import net.postchain.base.data.SqlUtils.isFatal
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.TransactionFailed
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.core.block.ManagedBlockBuilder
import java.sql.SQLException
import java.sql.Savepoint
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

/**
 * Wrapper around BlockBuilder providing more control over the process of building blocks,
 * with checks to see if current working block has been committed or not, and rolling back
 * database state in case some operation fails.
 *
 * Despite its name, it is not related to managed mode.
 *
 * @property eContext Connection context including blockchain and node identifiers
 * @property storage For database access
 * @property savepoint DB save point to rollback to in case block building fails
 * @property blockBuilder The base block builder
 * @property afterCommit Clean-up function to be called when block has been committed
 * @property closed Boolean for if block is open to further modifications and queries. It is closed if
 *           an operation fails to execute in full or if a witness is created and the block committed.
 *
 */
class BaseManagedBlockBuilder(
        private val eContext: EContext,
        private val savepoint: Savepoint,
        val storage: Storage,
        val blockBuilder: BlockBuilder,
        val beforeCommit: (BlockBuilder) -> Unit,
        val afterCommit: (BlockBuilder) -> Unit
) : ManagedBlockBuilder {
    companion object : KLogging()

    private var closed = false

    private var blocTrace: BlockTrace? = null // Only for logging, remains "null" unless TRACE

    private val storageLock = ReentrantLock()

    /**
     * Wrapper for block builder operations. Will close current working block for further modifications
     * if an operation fails to execute in full.
     *
     * @param RT type of returned object from called operation (Currently all Unit)
     * @param fn operation to be executed
     * @return whatever [fn] returns
     */
    private fun <RT> runOpSafely(fn: () -> RT): RT {
        if (closed) throw ProgrammerMistake("Already closed")

        try {
            return fn()
        } catch (e: SQLException) {
            if (e.isFatal()) {
                logger.error("Fatal database error occurred: ${e.message}")
                if (storage.exitOnFatalError) {
                    exitProcess(1)
                }
                // no point in doing rollback here, since it will inevitably fail
            } else if (e.isClosed()) {
                logger.error("Database connection has been closed")
                // no point in doing rollback here, since it will inevitably fail
            } else {
                rollback()
            }
            throw e
        } catch (e: Exception) {
            rollback()
            throw e
        }
    }

    override fun begin(partialBlockHeader: BlockHeader?) {
        runOpSafely { blockBuilder.begin(partialBlockHeader) }
    }

    override fun appendTransaction(tx: Transaction) {
        runOpSafely { blockBuilder.appendTransaction(tx) }
    }

    /**
     * Append transaction as long as everything is OK. withSavepoint will roll back any potential changes
     * to the database state if appendTransaction fails to complete
     *
     * @param tx Transaction to be added to the current working block
     * @return exception if error occurs
     */
    override fun maybeAppendTransaction(tx: Transaction): Exception? {
        val action = {
            blockBuilder.appendTransaction(tx)
        }

        return if (storage.isSavepointSupported()) {
            storage.withSavepoint(eContext, action).also {
                if (it != null) {
                    when (it) {
                        // Don't log stacktrace
                        is TransactionIncorrect -> logger.debug { "Tx Incorrect ${tx.getRID().toHex()}." }
                        is TransactionFailed -> logger.debug { "Tx failed ${tx.getRID().toHex()}." }
                        is UserMistake -> logger.debug(it) { "Failed to append transaction ${tx.getRID().toHex()} due to ${it.message}." }
                        // Should be unusual, so let's log everything
                        else -> logger.error("Failed to append transaction ${tx.getRID().toHex()} due to ${it.message}.", it)
                    }
                }
            }
        } else {
            logger.warn("Savepoint not supported! Unclear if Postchain will work under these conditions")
            action()
            null
        }
    }

    override fun finalizeBlock(timestamp: Long): BlockHeader {
        return runOpSafely { blockBuilder.finalizeBlock(timestamp) }
    }

    override fun finalizeAndValidate(blockHeader: BlockHeader) {
        runOpSafely { blockBuilder.finalizeAndValidate(blockHeader) }
    }

    override fun getBlockData(): BlockData {
        return blockBuilder.getBlockData()
    }

    override fun getBlockWitnessBuilder(): BlockWitnessBuilder? {
        if (closed) throw ProgrammerMistake("Already closed")
        return blockBuilder.getBlockWitnessBuilder()
    }

    override fun commit(blockWitness: BlockWitness) {
        commitLog("Start")
        getOrBuildBlockTrace()

        storageLock.withLock {
            if (!closed) {
                commitLog("Got lock")
                beforeCommit(blockBuilder)
                runOpSafely { blockBuilder.commit(blockWitness) }
                storage.closeWriteConnection(eContext, true)
                closed = true
                commitLog("Do after commit")
                afterCommit(blockBuilder)
            }
        }

        commitLog("End")
    }

    override val height: Long?
        get() = blockBuilder.height

    override fun rollback() {
        rollbackDebugLog("Start")
        storageLock.withLock {
            if (!closed) {
                rollbackLog("Got lock")
                if (!eContext.conn.isClosed) {
                    eContext.conn.rollback(savepoint)
                } else {
                    logger.error("Unable to rollback since connection is already closed")
                }
                closed = true
            }
        }
        rollbackDebugLog("End")
    }


    // --------------------------------

    // Only used during logging
    override fun getBTrace(): BlockTrace? {
        return blocTrace
    }

    // Only used during logging
    override fun setBTrace(bTrace: BlockTrace) {
        if (blocTrace == null) {
            blocTrace = bTrace
        } else {
            // Update existing object with missing info
            blocTrace!!.addDataIfMissing(bTrace)
        }
    }

    /**
     * We are a "[BlockBuilder]" but we are also decorating the "real" [BlockBuilder] so
     * the inner one should get the [BlockTrace] data from us.
     */
    private fun getOrBuildBlockTrace() {
        if (logger.isTraceEnabled) {
            if (getBTrace() != null) {
                val inner = blockBuilder.getBTrace()
                if (inner != null) {
                    inner.addDataIfMissing(getBTrace())
                } else {
                    blockBuilder.setBTrace(getBTrace()!!)
                }
            }
        }
    }

    private fun commitLog(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("${eContext.chainID} commit() -- $str, from block: ${getBTrace()}")
        }
    }

    private fun rollbackLog(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("${eContext.chainID} rollback() -- $str, from block: ${getBTrace()}")
        }
    }

    private fun rollbackDebugLog(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("${eContext.chainID} rollback() -- $str")
        }
        if (logger.isTraceEnabled) {
            logger.trace("${eContext.chainID} rollback() -- $str, from block: ${getBTrace()}")
        }
    }
}

typealias BaseManagedBlockBuilderProvider = (
        eContext: EContext,
        savepoint: Savepoint,
        storage: Storage,
        blockBuilder: BlockBuilder,
        beforeCommit: (BlockBuilder) -> Unit,
        afterCommit: (BlockBuilder) -> Unit) -> BaseManagedBlockBuilder