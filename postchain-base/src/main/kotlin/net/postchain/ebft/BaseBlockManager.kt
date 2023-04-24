// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.getConfigHash
import net.postchain.base.extension.getFailedConfigHash
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.worker.WorkerContext
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages intents and acts as a wrapper for [BlockDatabase] and [StatusManager]
 */
class BaseBlockManager(
        private val processName: BlockchainProcessName,
        private val blockDB: BlockDatabase,
        private val statusManager: StatusManager,
        private val blockStrategy: BlockBuildingStrategy,
        private val workerContext: WorkerContext
) : BlockManager {

    private var isOperationRunning = AtomicBoolean(false)

    @Volatile
    private var intent: BlockIntent = DoNothingIntent

    // Will be set to non-null value after the first block-db operation.
    override var lastBlockTimestamp: Long? = null

    companion object : KLogging()

    @Volatile
    override var currentBlock: BlockData? = null

    private fun <RT> runDBOp(op: () -> CompletionStage<RT>, onSuccess: (RT) -> Unit, onFailure: (Throwable) -> Unit = {}) {
        if (isOperationRunning.compareAndSet(false, true)) {
            intent = DoNothingIntent
            op().whenCompleteUnwrapped { res, throwable ->
                if (throwable == null) {
                    onSuccessfulOperation(res, onSuccess)
                } else {
                    onFailedOperation(throwable, onFailure)
                }
            }
        }
    }

    private fun onFailedOperation(throwable: Throwable, onFailure: (Throwable) -> Unit) {
        synchronized(statusManager) {
            onFailure(throwable)
            isOperationRunning.set(false)
        }
    }

    private fun <RT> onSuccessfulOperation(res: RT, onSuccess: (RT) -> Unit) {
        synchronized(statusManager) {
            onSuccess(res)
            isOperationRunning.set(false)
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
                        logger.trace { "onReceivedBlockAtHeight() - Creating block trace with procname: $processName , height: $height " }
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
                    } else if (exception is BadDataMistake && exception.type == BadDataType.CONFIGURATION_MISMATCH) {
                        val bcConfigProvider = workerContext.blockchainConfigurationProvider as? ManagedBlockchainConfigurationProvider
                        if (bcConfigProvider != null && bcConfigProvider.isPcuEnabled()) {
                            val bcConfig = workerContext.blockchainConfiguration
                            val incomingBlockConfigHash = block.header.getConfigHash()?.wrap()

                            withReadConnection(workerContext.engine.storage, bcConfig.chainID) { ctx ->
                                val isMyConfigPending = bcConfigProvider.isConfigPending(
                                        ctx, bcConfig.blockchainRid, statusManager.myStatus.height, bcConfig.configHash
                                )

                                val lastBlockHeight = statusManager.myStatus.height - 1
                                val lastBlockConfigHash = blockDB.getBlockAtHeight(lastBlockHeight, false).get()
                                        ?.header?.getConfigHash()?.wrap()

                                if (isMyConfigPending && incomingBlockConfigHash == lastBlockConfigHash) {
                                    // early adopter
                                    logger.info("Wrong config used. Chain will be restarted")
                                    workerContext.restartNotifier.notifyRestart(false)
                                } else if (bcConfigProvider.activeBlockNeedsConfigurationChange(ctx, bcConfig.chainID, true)) {
                                    // late adopter
                                    logger.info("Wrong config used. Chain will be restarted")
                                    workerContext.restartNotifier.notifyRestart(true)
                                }
                            }
                        }
                    } else if (exception is BadDataMistake && exception.type == BadDataType.FAILED_CONFIGURATION_MISMATCH) {
                        val bcConfigProvider = workerContext.blockchainConfigurationProvider as? ManagedBlockchainConfigurationProvider
                        if (bcConfigProvider != null && bcConfigProvider.isPcuEnabled()) {
                            val bcConfig = workerContext.blockchainConfiguration
                            val incomingBlockFailedConfigHash = block.header.getFailedConfigHash()?.wrap()
                            if (incomingBlockFailedConfigHash == null) {
                                // We seem to be an early adopter of failed config, push reporting to the future
                                withWriteConnection(workerContext.engine.storage, bcConfig.chainID) { ctx ->
                                    DatabaseAccess.of(ctx).apply {
                                        // Check if we can push failure reporting to next block
                                        val storedFaultyConfig = getFaultyConfiguration(ctx)
                                        if (storedFaultyConfig != null && storedFaultyConfig.reportAtHeight == height) {
                                            logger.info("Push reporting of failing config to the next block")
                                            updateFaultyConfigurationReportHeight(ctx, height + 1)
                                        }
                                    }
                                    true
                                }
                            } else if (incomingBlockFailedConfigHash != bcConfig.configHash.wrap()) {
                                withReadConnection(workerContext.engine.storage, bcConfig.chainID) { ctx ->
                                    val isIncomingFaultyConfigPending = bcConfigProvider.isConfigPending(
                                            ctx, bcConfig.blockchainRid, statusManager.myStatus.height, bcConfig.configHash
                                    )

                                    if (isIncomingFaultyConfigPending) {
                                        // Let's also attempt to load the potentially faulty pending config
                                        logger.info("Try to load potentially failing pending config")
                                        workerContext.restartNotifier.notifyRestart(true)
                                    }
                                }
                            }
                        }
                    } else {
                        logger.error(msg)
                    }
                })
            }
        }
    }

    private fun update() {
        synchronized(statusManager) {
            if (isOperationRunning.get()) return
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
                        logger.trace("$processName: Schedule build block. ${statusManager.myStatus.height}")
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
                        val msg = "$processName: Can't build block at height ${statusManager.myStatus.height}: ${exception.message}"
                        if (exception is PmEngineIsAlreadyClosed) {
                            logger.debug(msg)
                        } else {
                            logger.error(msg, exception)
                        }
                        blockStrategy.blockFailed()
                    })
                }

                else -> intent = blockIntent
            }
        }
    }

    override fun processBlockIntent(): BlockIntent {
        update()
        return intent
    }

    override fun getBlockIntent(): BlockIntent = intent

    private fun blockTimestamp(block: BlockData) = (block.header as BaseBlockHeader).timestamp

    // DEBUG only
    private fun blockTrace(blockIntent: BlockIntent) {
        if (logger.isTraceEnabled) {
            try {
                val heightIntent: Long? = if (blockIntent is FetchBlockAtHeightIntent) {
                    blockIntent.height  // In this case we should know the height, let's add it
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