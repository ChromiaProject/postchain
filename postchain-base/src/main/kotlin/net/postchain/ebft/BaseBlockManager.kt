// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.common.toHex
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import nl.komponents.kovenant.Promise

/**
 * Manages intents and acts as a wrapper for [blockDatabase] and [statusManager]
 */
class BaseBlockManager(
    private val processName: BlockchainProcessName,
    private val blockDB: BlockDatabase,
    private val statusManager: StatusManager,
    val blockStrategy: BlockBuildingStrategy
) : BlockManager {

    @Volatile
    private var processing = false
    @Volatile
    private var intent: BlockIntent = DoNothingIntent

    // Will be set to non-null value after the first block-db operation.
    override var lastBlockTimestamp: Long? = null

    companion object : KLogging()

    @Volatile
    override var currentBlock: BlockData? = null

    private fun <RT> runDBOp(op: () -> Promise<RT, Exception>, onSuccess: (RT) -> Unit, onFailure: (Exception) -> Unit = {}) {
        if (!processing) {
            synchronized(statusManager) {
                processing = true
                intent = DoNothingIntent

                op() success { res ->
                    synchronized(statusManager) {
                        onSuccess(res)
                        processing = false
                    }
                } fail { err ->
                    synchronized(statusManager) {
                        onFailure(err)
                        processing = false
                        logger.debug(err) { "Error in runDBOp()" }
                    }
                }
            }
        }
    }

    override fun onReceivedUnfinishedBlock(block: BlockData) {
        synchronized(statusManager) {
            val theIntent = intent
            if (theIntent is FetchUnfinishedBlockIntent && theIntent.blockRID.contentEquals(block.header.blockRID)) {
                runDBOp({
                    blockTrace(theIntent)
                    blockDB.loadUnfinishedBlock(block)
                }, { signature ->
                    if (statusManager.onReceivedBlock(block.header.blockRID, signature)) {
                        currentBlock = block
                        lastBlockTimestamp = blockTimestamp(block)
                    }
                }, { exception ->
                    val msg = "$processName: Can't load unfinished block ${theIntent.blockRID.toHex()}: " +
                            "${exception.message}"
                    if (exception is PmEngineIsAlreadyClosed) {
                        logger.debug(msg)
                    } else {
                        logger.error(msg)
                    }
                })
            }
        }
    }

    override fun onReceivedBlockAtHeight(block: BlockDataWithWitness, height: Long) {
        synchronized(statusManager) {
            val theIntent = intent
            if (theIntent is FetchBlockAtHeightIntent && theIntent.height == height) {
                runDBOp({
                    val bTrace = if (logger.isTraceEnabled) {
                        BlockTrace.build(processName, block.header.blockRID, height)
                    } else {
                        null // Use null for performance
                    }

                    blockDB.addBlock(block, null, bTrace)
                }, {
                    if (statusManager.onHeightAdvance(height + 1)) {
                        currentBlock = null
                        lastBlockTimestamp = blockTimestamp(block)
                    }
                }, { exception ->
                    val msg = "$processName: Can't add received block ${block.header.blockRID.toHex()} " +
                            "at height $height: ${exception.message}"
                    if (exception is PmEngineIsAlreadyClosed) {
                        logger.debug(msg)
                    } else {
                        logger.error(msg)
                    }
                })
            }
        }
    }

    // this is called only in getBlockIntent which is synchronized on status manager
    private fun update() {
        if (processing) return
        val blockIntent = statusManager.getBlockIntent()
        intent = DoNothingIntent
        when (blockIntent) {

            is CommitBlockIntent -> {
                if (currentBlock == null) {
                    logger.error("$processName: Don't have a block StatusManager wants me to commit")
                    return
                }
                if (logger.isTraceEnabled) {
                    logger.trace("$processName: Schedule commit of block ${currentBlock!!.header.blockRID.toHex()}")
                }

                runDBOp({
                    blockTrace(blockIntent)
                    blockDB.commitBlock(statusManager.commitSignatures)
                }, {
                    statusManager.onCommittedBlock(currentBlock!!.header.blockRID)
                    lastBlockTimestamp = blockTimestamp(currentBlock!!)
                    currentBlock = null
                }, { exception ->
                    logger.error("$processName: Can't commit block ${currentBlock!!.header.blockRID.toHex()}: " +
                            "${exception.message}")
                })
            }

            is BuildBlockIntent -> {
                // It's our turn to build a block. But we need to consult the
                // BlockBuildingStrategy in order to figure out if this is the
                // right time. For example, the strategy may decide that
                // we won't build a block until we have at least three transactions
                // in the transaction queue. Or it will only build a block every 10 minutes.
                // Be careful not to have a BlockBuildingStrategy that conflicts with the
                // RevoltTracker of ValidatorSyncManager.
                if (!blockStrategy.shouldBuildBlock()) {
                    return
                }
                if (logger.isTraceEnabled) {
                    logger.trace("$processName: Schedule build block. ${statusManager.myStatus.height + 1}")
                }

                runDBOp({
                    blockTrace(blockIntent)
                    blockDB.buildBlock()
                }, { blockAndSignature ->
                    val block = blockAndSignature.first
                    val signature = blockAndSignature.second
                    if (statusManager.onBuiltBlock(block.header.blockRID, signature)) {
                        currentBlock = block
                        lastBlockTimestamp = blockTimestamp(block)
                    }
                }, { exception ->
                    val msg = "$processName: Can't build block at height ${statusManager.myStatus.height + 1}: ${exception.message}"
                    if (exception is PmEngineIsAlreadyClosed) {
                        logger.debug(msg)
                    } else {
                        logger.error(msg)
                    }
                })
            }

            else -> intent = blockIntent
        }
    }

    override fun processBlockIntent(): BlockIntent {
        synchronized(statusManager) {
            update()
        }
        return intent
    }

    override fun getBlockIntent(): BlockIntent = intent

    private fun blockTimestamp(block: BlockData) = (block.header as BaseBlockHeader).timestamp

    // DEBUG only
    private fun blockTrace(blockIntent: BlockIntent) {
        if (logger.isTraceEnabled) {
            try {
                val heightIntent: Long? = if (blockIntent is FetchBlockAtHeightIntent) {
                    (blockIntent as FetchBlockAtHeightIntent).height  // In this case we should know the height, let's add it
                } else {
                    null
                }
                blockDB.setBlockTrace(BlockTrace.build(processName, currentBlock?.header?.blockRID, heightIntent))
            } catch (e: java.lang.Exception) {
                // Doesn't matter
                logger.trace(e) { "$processName: ERROR where adding bTrace." }
            }
        }
    }
}