// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.TimeLog
import net.postchain.common.data.ByteArrayKey
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.core.block.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import nl.komponents.kovenant.task
import java.lang.Long.max

const val LOG_STATS = true // Was this the reason this entire class was muted?

/**
 * An [BlockchainEngine] will only produce [BlockBuilder]s for a single chain.
 * This class produces [ManagedBlockBuilder]s, which means we have to check for BC restart after a block is built.
 *
 * Usually we don't log single (successful) transactions, not even at trace level.
 */
open class BaseBlockchainEngine(
    private val processName: BlockchainProcessName,
    private val blockchainConfiguration: BlockchainConfiguration,
    val storage: Storage,
    private val chainID: Long,
    private val transactionQueue: TransactionQueue,
    private val useParallelDecoding: Boolean = true
) : BlockchainEngine {

    companion object : KLogging()

    private lateinit var strategy: BlockBuildingStrategy
    private lateinit var blockQueries: BlockQueries
    private var initialized = false
    private var closed = false
    private var afterCommitHandlerInternal: AfterCommitHandler = { _, _, _ -> false }
    private var afterCommitHandler: AfterCommitHandler = afterCommitHandlerInternal
    private val metrics = BaseBlockchainEngineMetrics(blockchainConfiguration.chainID, blockchainConfiguration.blockchainRid, transactionQueue)

    override fun isRunning() = !closed

    override fun initialize() {
        if (initialized) {
            throw ProgrammerMistake("Engine is already initialized")
        }
        blockQueries = blockchainConfiguration.makeBlockQueries(storage)
        strategy = blockchainConfiguration.getBlockBuildingStrategy(blockQueries, transactionQueue)
        initialized = true
    }

    override fun setAfterCommitHandler(handler: AfterCommitHandler) {
        afterCommitHandler = handler
    }

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

    override fun shutdown() {
        closed = true
        storage.close()
    }

    private fun makeBlockBuilder(): ManagedBlockBuilder {
        if (!initialized) throw ProgrammerMistake("Engine is not initialized yet")
        if (closed) throw PmEngineIsAlreadyClosed("Engine is already closed")
        val eContext = storage.openWriteConnection(chainID) // TODO: Close eContext

        return BaseManagedBlockBuilder(eContext, storage, blockchainConfiguration.makeBlockBuilder(eContext), { },
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
                })
    }

    override fun loadUnfinishedBlock(block: BlockData): Pair<ManagedBlockBuilder, Exception?> {
        return if (useParallelDecoding)
            parallelLoadUnfinishedBlock(block)
        else
            sequentialLoadUnfinishedBlock(block)
    }

    private fun smartDecodeTransaction(txData: ByteArray): Transaction {
        var tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(txData)
        val enqueuedTx = transactionQueue.findTransaction(ByteArrayKey(tx.getRID()))
        if (enqueuedTx != null && enqueuedTx.getHash().contentEquals(tx.getHash())) {
            // if transaction is identical (has same hash) then use transaction
            // from queue, which is already verified
            tx = enqueuedTx
        }

        return if (tx.isCorrect()) tx
        else throw TransactionIncorrect("Transaction is not correct")
    }

    private fun sequentialLoadUnfinishedBlock(block: BlockData): Pair<ManagedBlockBuilder, Exception?> {
        return loadUnfinishedBlockImpl(block) { txs ->
            txs.map { smartDecodeTransaction(it) }
        }
    }

    private fun parallelLoadUnfinishedBlock(block: BlockData): Pair<ManagedBlockBuilder, Exception?> {
        return loadUnfinishedBlockImpl(block) { txs ->
            val txsLazy = txs.map { tx ->
                task { smartDecodeTransaction(tx) }
            }

            txsLazy.map { it.get() }
        }
    }

    private fun loadUnfinishedBlockImpl(
            block: BlockData,
            transactionsDecoder: (List<ByteArray>) -> List<Transaction>
    ): Pair<ManagedBlockBuilder, Exception?> {

        val grossStart = System.nanoTime()
        val blockBuilder = makeBlockBuilder()
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

            if (LOG_STATS) {
                val prettyBlockHeader = prettyBlockHeader(
                        block.header, block.transactions.size, 0, grossStart to grossEnd, netStart to netEnd)
                logger.info("$processName: Loaded block: $prettyBlockHeader")
            }

            loadLog("End", blockBuilder.getBTrace())
        } catch (e: Exception) {
            exception = e
        }

        return blockBuilder to exception
    }

    override fun buildBlock(): Pair<ManagedBlockBuilder, Exception?> {
        TimeLog.startSum("BaseBlockchainEngine.buildBlock().buildBlock")
        buildLog("Begin")
        val grossStart = System.nanoTime()

        val blockBuilder = makeBlockBuilder()
        var exception: Exception? = null

        try {
            val blockSample = Timer.start(Metrics.globalRegistry)

            blockBuilder.begin(null)
            val abstractBlockBuilder = ((blockBuilder as BaseManagedBlockBuilder).blockBuilder as AbstractBlockBuilder)
            val netStart = System.nanoTime()

            TimeLog.startSum("BaseBlockchainEngine.buildBlock().appendTransactions")
            var acceptedTxs = 0
            var rejectedTxs = 0

            while (true) {
                if (logger.isTraceEnabled) {
                    logger.trace("$processName: Checking transaction queue")
                }
                TimeLog.startSum("BaseBlockchainEngine.buildBlock().takeTransaction")
                val tx = transactionQueue.takeTransaction()
                TimeLog.end("BaseBlockchainEngine.buildBlock().takeTransaction")
                if (tx != null) {
                    logger.trace { "$processName: Appending transaction ${tx.getRID().toHex()}" }
                    val transactionSample = Timer.start(Metrics.globalRegistry)
                    TimeLog.startSum("BaseBlockchainEngine.buildBlock().maybeApppendTransaction")
                    if (tx.isSpecial()) {
                        rejectedTxs++
                        transactionQueue.rejectTransaction(tx, ProgrammerMistake("special transactions can't enter queue"))
                        continue
                    }
                    val txException = blockBuilder.maybeAppendTransaction(tx)
                    TimeLog.end("BaseBlockchainEngine.buildBlock().maybeAppendTransaction")
                    if (txException != null) {
                        rejectedTxs++
                        transactionSample.stop(metrics.rejectedTransactions)
                        transactionQueue.rejectTransaction(tx, txException)
                        logger.warn("Rejected Tx: ${ByteArrayKey(tx.getRID())}, reason: ${txException.message}, cause: ${txException.cause}")
                    } else {
                        acceptedTxs++
                        transactionSample.stop(metrics.acceptedTransactions)
                        // tx is fine, consider stopping
                        if (strategy.shouldStopBuildingBlock(abstractBlockBuilder)) {
                            buildDebug("Block size limit is reached")
                            break
                        }
                    }
                } else { // tx == null
                    break
                }
            }

            TimeLog.end("BaseBlockchainEngine.buildBlock().appendTransactions")

            val netEnd = System.nanoTime()
            val blockHeader = blockBuilder.finalizeBlock()
            val grossEnd = System.nanoTime()

            TimeLog.end("BaseBlockchainEngine.buildBlock().buildBlock")

            if (LOG_STATS) {
                val prettyBlockHeader = prettyBlockHeader(
                        blockHeader, acceptedTxs, rejectedTxs, grossStart to grossEnd, netStart to netEnd)
                logger.info("$processName: Block is finalized: $prettyBlockHeader")
            } else {
                logger.info("$processName: Block is finalized")
            }

            if (logger.isTraceEnabled) {
                blockBuilder.setBTrace(getBlockTrace(blockHeader))
                buildLog("End", blockBuilder.getBTrace())
            }

            blockSample.stop(metrics.blocks)
        } catch (e: Exception) {
            exception = e
        }
        buildLog("End")

        return blockBuilder to exception
    }

    // -----------------
    // Logging only
    // -----------------

    private fun prettyBlockHeader(
            blockHeader: BlockHeader,
            acceptedTxs: Int,
            rejectedTxs: Int,
            gross: Pair<Long, Long>,
            net: Pair<Long, Long>
    ): String {

        val grossRate = (acceptedTxs * 1_000_000_000L) / max(gross.second - gross.first, 1)
        val netRate = (acceptedTxs * 1_000_000_000L) / max(net.second - net.first, 1)
        val grossTimeMs = (gross.second - gross.first) / 1_000_000

        val gtvBlockHeader = GtvDecoder.decodeGtv(blockHeader.rawData)
        val blockHeaderData = BlockHeaderData.fromGtv(gtvBlockHeader as GtvArray)

        return "$grossTimeMs ms" +
                ", $netRate net tps" +
                ", $grossRate gross tps" +
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
        return BlockTrace.build(null, blockHeader.blockRID, blockHeaderData.gtvHeight.asInteger())
    }

    private fun afterLog(str: String, bTrace: BlockTrace?) {
        if (logger.isTraceEnabled) {
            logger.trace { "$processName After-commit-hook: $str, coming from block: $bTrace" }
        }
    }

    private fun loadLog(str: String, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            logger.debug { "$processName loadUnfinishedBlockImpl() -- $str, coming from block: $bTrace" }
        }
    }

    private fun buildLog(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug { "$processName buildBlock() -- $str" }
        }
    }

    private fun buildLog(str: String, bTrace: BlockTrace?) {
        if (logger.isDebugEnabled) {
            logger.debug { "$processName buildBlock() -- $str, for block: $bTrace" }
        }
    }

    private fun buildDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug { "$processName buildBlock() - $str" }
        }
    }
}