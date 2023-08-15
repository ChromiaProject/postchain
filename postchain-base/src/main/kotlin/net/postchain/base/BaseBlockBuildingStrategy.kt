// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.concurrent.util.get
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import java.time.Clock
import kotlin.math.min
import kotlin.math.pow

open class BaseBlockBuildingStrategy(val configData: BaseBlockBuildingStrategyConfigurationData,
                                     blockQueries: BlockQueries,
                                     private val txQueue: TransactionQueue,
                                     private val clock: Clock
) : BlockBuildingStrategy {

    private var lastBlockTime: Long
    private var firstTxTime = 0L

    private var failedBlockTime: Long = 0
    private var failedBlockCount = 0

    private val maxBlockTime = configData.maxBlockTime
    private val maxBlockTransactions = configData.maxBlockTransactions
    private val maxTxDelay = configData.maxTxDelay
    private val minInterBlockInterval = configData.minInterBlockInterval
    private val minBackoffTime = configData.minBackoffTime
    private val maxBackoffTime = configData.maxBackoffTime
    private val preemptiveBlockBuilding = configData.preemptiveBlockBuilding

    init {
        val height = blockQueries.getLastBlockHeight().get()
        lastBlockTime = if (height == -1L) {
            0
        } else {
            val blockRID = blockQueries.getBlockRid(height).get()!!
            (blockQueries.getBlockHeader(blockRID).get() as BaseBlockHeader).timestamp
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        val baseBlockBuilder = bb as BaseBlockBuilder
        return baseBlockBuilder.shouldStopBuildingBlock(maxBlockTransactions)
    }

    override fun blockFailed() {
        failedBlockTime = currentTimeMillis()
        failedBlockCount++
    }

    override fun blockCommitted(blockData: BlockData) {
        lastBlockTime = (blockData.header as BaseBlockHeader).timestamp
        firstTxTime = 0
        failedBlockCount = 0
        failedBlockTime = 0
    }

    override fun preemptiveBlockBuilding(): Boolean = preemptiveBlockBuilding

    override fun shouldBuildBlock(): Boolean {
        val now = currentTimeMillis()

        if (mustWaitMinimumBuildBlockTime() > 0) return false
        if (now - lastBlockTime > maxBlockTime) return true
        if (firstTxTime > 0 && now - firstTxTime > maxTxDelay) return true

        val transactionQueueSize = txQueue.getTransactionQueueSize()
        if (transactionQueueSize >= maxBlockTransactions) return true
        if (firstTxTime == 0L && transactionQueueSize > 0) {
            firstTxTime = now
            return false
        }

        return extendedShouldBuildBlock()
    }

    override fun mustWaitMinimumBuildBlockTime(): Long {
        val now = currentTimeMillis()
        return if (now - lastBlockTime < minInterBlockInterval) return minInterBlockInterval - (now - lastBlockTime) else 0
    }

    override fun mustWaitBeforeBuildBlock(): Boolean {
        val now = currentTimeMillis()
        return failedBlockTime > 0 && now - failedBlockTime < getBackoffTime()
    }

    private fun currentTimeMillis(): Long = clock.millis()

    open fun extendedShouldBuildBlock(): Boolean = false

    fun getBackoffTime(): Long = min(2.0.pow(failedBlockCount).toLong() + minBackoffTime, maxBackoffTime)
}