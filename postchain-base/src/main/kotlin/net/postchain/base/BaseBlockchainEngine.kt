// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.configuration.FaultyConfiguration
import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BeforeCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.EContext
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.ManagedBlockBuilder
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.ErrorDiagnosticValue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.logging.BLOCK_RID_TAG
import net.postchain.metrics.BaseBlockchainEngineMetrics
import net.postchain.metrics.DelayTimer
import java.lang.Long.max
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.time.Duration.Companion.minutes

/**
 * An [BlockchainEngine] will only produce [BlockBuilder]s for a single chain.
 * This class produces [ManagedBlockBuilder]s, which means we have to check for BC restart after a block is built.
 *
 * Usually we don't log single (successful) transactions, not even at trace level.
 */
open class BaseBlockchainEngine(
        private val blockchainConfiguration: BlockchainConfiguration,
        final override val blockBuilderStorage: Storage,
        final override val sharedStorage: Storage,
        override val chainID: Long,
        initialEContext: EContext,
        private val blockchainConfigurationProvider: BlockchainConfigurationProvider,
        private val restartNotifier: BlockchainRestartNotifier,
        private val nodeDiagnosticContext: NodeDiagnosticContext,
        private val beforeCommitHandler: BeforeCommitHandler,
        private val afterCommitHandler: AfterCommitHandler,
        private val useParallelDecoding: Boolean = true,
) : BlockchainEngine {

    companion object : KLogging()

    private val blockQueries: BlockQueries = blockchainConfiguration.makeBlockQueries(sharedStorage)
    private val transactionQueue = BaseTransactionQueue(
            blockchainConfiguration.transactionQueueSize,
            recheckThreadInterval = 1.minutes,
            recheckTxInterval = blockchainConfiguration.transactionQueueRecheckInterval,
            Clock.systemUTC(),
            if (blockchainConfiguration.hasQuery(PRIORITIZE_QUERY_NAME))
                BaseTransactionPrioritizer { name: String, args: Gtv -> blockQueries.query(name, args).toCompletableFuture().get() }
            else
                null
    )
    private val strategy: BlockBuildingStrategy = blockchainConfiguration.getBlockBuildingStrategy(blockQueries, transactionQueue)
    private val metrics = BaseBlockchainEngineMetrics(blockchainConfiguration.chainID, blockchainConfiguration.blockchainRid, transactionQueue)

    private var closed = false
    private var currentEContext = initialEContext
    private var hasBuiltFirstBlockAfterConfigUpdate = false

    init {
        hasBuiltFirstBlockAfterConfigUpdate = withReadConnection(blockBuilderStorage, blockchainConfiguration.chainID) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val configIsSaved = db.configurationHashExists(ctx, blockchainConfiguration.configHash)
            if (!configIsSaved) {
                false
            } else {
                val activeHeight = db.getLastBlockHeight(ctx) + 1
                val configHeight = db.findConfigurationHeightForBlock(ctx, activeHeight) ?: 0
                activeHeight > configHeight
            }
        }
    }

    override fun isRunning() = !closed

    override fun getTransactionQueue(): TransactionQueue {
        return transactionQueue
    }

    override fun getBlockQueries(): BlockQueries {
        return blockQueries
    }

    override fun getBlockBuildingStrategy(): BlockBuildingStrategy {
        return strategy
    }

    override fun getConfiguration(): BlockchainConfiguration {
        return blockchainConfiguration
    }

    override fun hasBuiltFirstBlockAfterConfigUpdate(): Boolean {
        return hasBuiltFirstBlockAfterConfigUpdate
    }

    override val blockchainRid: BlockchainRid
        get() = blockchainConfiguration.blockchainRid

    override fun shutdown() {
        closed = true
        blockchainConfiguration.shutdownModules()
        blockQueries.shutdown()
        if (!currentEContext.conn.isClosed) {
            blockBuilderStorage.closeWriteConnection(currentEContext, false)
        }
        transactionQueue.close()
        metrics.close()
    }

    private fun makeBlockBuilder(isSyncing: Boolean): BaseManagedBlockBuilder {
        if (closed) throw PmEngineIsAlreadyClosed("Engine is already closed")
        currentEContext = if (currentEContext.conn.isClosed) {
            blockBuilderStorage.openWriteConnection(chainID)
        } else {
            currentEContext
        }
        val savepoint = currentEContext.conn.setSavepoint("blockBuilder${System.nanoTime()}")

        return BaseManagedBlockBuilder(currentEContext, savepoint, blockBuilderStorage, blockchainConfiguration.makeBlockBuilder(currentEContext, isSyncing),
                {
                    val blockBuilder = it as AbstractBlockBuilder
                    beforeCommitHandler(blockBuilder.getBTrace(), blockBuilder.bctx)
                },
                {
                    afterLog("Begin", it.getBTrace())
                    val blockBuilder = it as AbstractBlockBuilder
                    transactionQueue.removeAll(blockBuilder.transactions)
                    strategy.blockCommitted(blockBuilder.getBlockData())
                    if (afterCommitHandler(
                                    blockBuilder.getBTrace(), // This is a big reason for BTrace to exist
                                    blockBuilder.bctx.height,
                                    blockBuilder.bctx.timestamp)) {
                        closed = true
                    }
                    afterLog("End", it.getBTrace())
                    hasBuiltFirstBlockAfterConfigUpdate = true
                })
    }

    override fun loadUnfinishedBlock(block: BlockData, isSyncing: Boolean): Pair<ManagedBlockBuilder, Exception?> {
        return if (useParallelDecoding)
            parallelLoadUnfinishedBlock(block, isSyncing)
        else
            sequentialLoadUnfinishedBlock(block, isSyncing)
    }

    private fun sequentialLoadUnfinishedBlock(block: BlockData, isSyncing: Boolean): Pair<ManagedBlockBuilder, Exception?> {
        return loadUnfinishedBlockImpl(block, isSyncing) { txs ->
            txs.map { smartDecodeTransaction(it) }
        }
    }

    private fun parallelLoadUnfinishedBlock(block: BlockData, isSyncing: Boolean): Pair<ManagedBlockBuilder, Exception?> {
        return loadUnfinishedBlockImpl(block, isSyncing) { txs ->
            txs.parallelStream().map { smartDecodeTransaction(it) }.collect(Collectors.toList())
        }
    }

    private fun smartDecodeTransaction(txData: ByteArray): Transaction {
        var tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(txData)
        val enqueuedTx = transactionQueue.findTransaction(WrappedByteArray(tx.getRID()))
        if (enqueuedTx != null && enqueuedTx.getHash().contentEquals(tx.getHash())) {
            // if transaction is identical (has same hash) then use transaction
            // from queue, which is already verified
            tx = enqueuedTx
        }

        tx.checkCorrectness()
        return tx
    }

    private fun loadUnfinishedBlockImpl(
            block: BlockData,
            isSyncing: Boolean,
            transactionsDecoder: (List<ByteArray>) -> List<Transaction>
    ): Pair<ManagedBlockBuilder, Exception?> {
        val grossStart = System.nanoTime()
        val blockBuilder = makeBlockBuilder(isSyncing)
        var exception: Exception? = null

        try {
            loadLog("Start", blockBuilder.getBTrace())
            if (logger.isTraceEnabled) {
                blockBuilder.setBTrace(getBlockTrace(block.header))
            }
            blockBuilder.begin(block.header)

            val netStart = System.nanoTime()
            val decodedTxs = transactionsDecoder(block.transactions)
            decodedTxs.forEach(blockBuilder::appendTransaction)
            val netEnd = System.nanoTime()

            blockBuilder.finalizeAndValidate(block.header)
            val grossEnd = System.nanoTime()

            val prettyBlockHeader = prettyBlockHeader(
                    block.header, block.transactions.size, 0, grossStart to grossEnd, netStart to netEnd, 0
            )
            logger.info("Loaded block: $prettyBlockHeader")

            loadLog("End", blockBuilder.getBTrace())
        } catch (e: Exception) {
            try {
                blockBuilder.rollback()
            } catch (ignore: Exception) {
            }
            nodeDiagnosticContext.blockchainErrorQueue(blockchainConfiguration.blockchainRid).add(
                    ErrorDiagnosticValue(
                            e.message ?: "Failed to load unfinished block",
                            System.currentTimeMillis(),
                            blockBuilder.height
                    )
            )
            exception = e
        }

        return blockBuilder to exception
    }

    override fun buildBlock(): Pair<ManagedBlockBuilder, Exception?> {
        buildLog("Begin")
        val grossStart = System.nanoTime()

        val blockBuilder = makeBlockBuilder(false)
        var exception: Exception? = null

        try {
            buildBlockInternal(blockBuilder, grossStart)
        } catch (e: Exception) {
            try {
                blockBuilder.rollback()
            } catch (ignore: Exception) {
            }
            try {
                if (hasBuiltInitialBlock()) {
                    if (!hasBuiltFirstBlockAfterConfigUpdate) {
                        revertConfiguration(blockBuilder.height, blockchainConfiguration.configHash)
                    } else {
                        // See if we have a configuration update that potentially can fix our block building issues
                        checkForNewConfiguration()
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Unable to revert configuration: $e" }
            }
            nodeDiagnosticContext.blockchainErrorQueue(blockchainConfiguration.blockchainRid).add(
                    ErrorDiagnosticValue(
                            e.message ?: "Failed to build block",
                            System.currentTimeMillis(),
                            blockBuilder.height)
            )

            exception = e
        }
        buildLog("End")

        return blockBuilder to exception
    }

    private fun checkForNewConfiguration() {
        withReadConnection(blockBuilderStorage, chainID) { ctx ->
            if (blockchainConfigurationProvider.activeBlockNeedsConfigurationChange(ctx, chainID, false)) {
                logger.debug("Found new configuration at current height. Will restart and apply it.")
                restartNotifier.notifyRestart(false)
                closed = true
            }
        }
    }

    private fun buildBlockInternal(blockBuilder: BaseManagedBlockBuilder, grossStart: Long) {
        val blockStart = System.nanoTime()

        blockBuilder.begin(null)
        val netStart = System.nanoTime()

        var acceptedTxs = 0
        var rejectedTxs = 0

        val delayTimer = DelayTimer()

        while (true) {
            logger.trace { "Checking transaction queue" }
            val tx = transactionQueue.takeTransaction()
            if (tx != null) {
                delayTimer.stop()
                logger.trace { "Appending transaction ${tx.getRID().toHex()}" }
                val transactionSample = Timer.start(Metrics.globalRegistry)
                if (tx.isSpecial()) {
                    rejectedTxs++
                    transactionQueue.rejectTransaction(
                            tx,
                            ProgrammerMistake("special transactions can't enter queue")
                    )
                    continue
                }
                val txException = blockBuilder.maybeAppendTransaction(tx)
                if (txException != null) {
                    rejectedTxs++
                    transactionSample.stop(metrics.rejectedTransactions)
                    transactionQueue.rejectTransaction(tx, txException)
                    val rejectedMsg = "Rejected Tx: ${tx.getRID().toHex()}, reason: ${txException.message}, cause: ${txException.cause}"
                    if (txException is UserMistake) {
                        logger.info(rejectedMsg)
                    } else {
                        logger.warn(rejectedMsg)
                    }
                } else {
                    acceptedTxs++
                    transactionSample.stop(metrics.acceptedTransactions)
                    // tx is fine, consider stopping
                    if (strategy.shouldStopBuildingBlock(blockBuilder.blockBuilder)) {
                        logger.debug { "buildBlock() - Block limit is reached" }
                        val mustWaitTime = mustWaitMinimumBuildBlockTime()
                        if (mustWaitTime > 0) {
                            Thread.sleep(mustWaitTime)
                            delayTimer.add(mustWaitTime * 1_000_000)
                        }
                        break
                    }
                }
            } else {
                if (shouldBuildBlock()) { // tx == null
                    delayTimer.stop()
                    break
                } else {
                    delayTimer.start()
                }
            }
        }

        val netEnd = System.nanoTime()
        val blockHeader = blockBuilder.finalizeBlock()
        val grossEnd = System.nanoTime()

        withLoggingContext(BLOCK_RID_TAG to blockHeader.blockRID.toHex()) {
            val prettyBlockHeader = prettyBlockHeader(
                    blockHeader, acceptedTxs, rejectedTxs, grossStart to grossEnd, netStart to netEnd, delayTimer.totalDelayTime
            )
            logger.info("Block is finalized: $prettyBlockHeader")
            nodeDiagnosticContext.blockchainBlockStats(blockchainRid).add(DiagnosticData(
                    DiagnosticProperty.BLOCK_RID withValue blockHeader.blockRID.toHex(),
                    DiagnosticProperty.BLOCK_HEIGHT withValue blockBuilder.height,
                    DiagnosticProperty.BLOCK_BUILDER withValue true,
                    DiagnosticProperty.BLOCK_SIGNER withValue true,
            ))

            if (logger.isTraceEnabled) {
                blockBuilder.setBTrace(getBlockTrace(blockHeader))
                buildLog("End", blockBuilder.getBTrace())
            }
        }

        val blockEndTime = System.nanoTime() - blockStart - delayTimer.totalDelayTime
        metrics.blocks.record(blockEndTime, TimeUnit.NANOSECONDS)
    }

    private fun shouldBuildBlock() = !strategy.preemptiveBlockBuilding() || strategy.shouldBuildBlock()

    private fun mustWaitMinimumBuildBlockTime() = if (strategy.preemptiveBlockBuilding()) strategy.mustWaitMinimumBuildBlockTime() else 0

    private fun hasBuiltInitialBlock() = DatabaseAccess.of(currentEContext).getLastBlockHeight(currentEContext) > -1L

    private fun revertConfiguration(blockHeight: Long?, failedConfigHash: ByteArray) {
        logger.info("Reverting faulty configuration with hash ${failedConfigHash.toHex()} at height $blockHeight")
        currentEContext.conn.rollback() // rollback any DB updates the new and faulty configuration did
        blockHeight?.let {
            DatabaseAccess.of(currentEContext).apply {
                addFaultyConfiguration(currentEContext, FaultyConfiguration(failedConfigHash.wrap(), blockHeight))
                removeConfiguration(currentEContext, it)
            }
        }
        blockBuilderStorage.closeWriteConnection(currentEContext, true)

        restartNotifier.notifyRestart(false)
        closed = true
    }

    // -----------------
    // Logging only
    // -----------------

    private fun prettyBlockHeader(
            blockHeader: BlockHeader,
            acceptedTxs: Int,
            rejectedTxs: Int,
            gross: Pair<Long, Long>,
            net: Pair<Long, Long>,
            delayTime: Long
    ): String {
        val grossRate = (acceptedTxs * 1_000_000_000L) / max(gross.second - gross.first - delayTime, 1)
        val netRate = (acceptedTxs * 1_000_000_000L) / max(net.second - net.first - delayTime, 1)
        val grossTimeMs = (gross.second - gross.first - delayTime) / 1_000_000
        val delayTimeMs = delayTime / 1_000_000

        val gtvBlockHeader = GtvDecoder.decodeGtv(blockHeader.rawData)
        val blockHeaderData = BlockHeaderData.fromGtv(gtvBlockHeader as GtvArray)

        return "$grossTimeMs ms" +
                ", $netRate net tps" +
                ", $grossRate gross tps" +
                ", delay: $delayTimeMs ms" +
                ", height: ${blockHeaderData.gtvHeight.asInteger()}" +
                ", accepted txs: $acceptedTxs" +
                ", rejected txs: $rejectedTxs" +
                ", root-hash: ${blockHeaderData.getMerkleRootHash().toHex()}" +
                ", block-rid: ${blockHeader.blockRID.toHex()}" +
                ", prev-block-rid: ${blockHeader.prevBlockRID.toHex()}"
    }

    /**
     * @return a [BlockTrace] holding as much info we can get about the block
     */
    private fun getBlockTrace(blockHeader: BlockHeader): BlockTrace {
        val gtvBlockHeader = GtvDecoder.decodeGtv(blockHeader.rawData)
        val blockHeaderData = BlockHeaderData.fromGtv(gtvBlockHeader as GtvArray)
        return BlockTrace.build(blockHeader.blockRID, blockHeaderData.gtvHeight.asInteger())
    }

    private fun afterLog(str: String, bTrace: BlockTrace?) {
        logger.trace { "After-commit-hook: $str, coming from block: $bTrace" }
    }

    private fun loadLog(str: String, bTrace: BlockTrace?) {
        logger.debug { "loadUnfinishedBlockImpl() -- $str, coming from block: $bTrace" }
    }

    private fun buildLog(str: String) {
        logger.debug { "buildBlock() -- $str" }
    }

    private fun buildLog(str: String, bTrace: BlockTrace?) {
        logger.debug { "buildBlock() -- $str, for block: $bTrace" }
    }
}
