// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.concurrent.util.get
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import kotlin.math.min
import kotlin.math.pow

class BaseBlockBuildingStrategy(val configData: BaseBlockBuildingStrategyConfigurationData,
                                blockQueries: BlockQueries,
                                private val txQueue: TransactionQueue
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
        failedBlockTime = System.currentTimeMillis()
        failedBlockCount++
    }

    override fun blockCommitted(blockData: BlockData) {
        lastBlockTime = (blockData.header as BaseBlockHeader).timestamp
        firstTxTime = 0
        failedBlockCount = 0
        failedBlockTime = 0
    }

    override fun shouldBuildBlock(): Boolean {
        val now = System.currentTimeMillis()

        if (failedBlockTime > 0 && now - failedBlockTime < getBackoffTime()) return false
        if (now - lastBlockTime > maxBlockTime) return true
        if (now - lastBlockTime < minInterBlockInterval) return false
        if (firstTxTime > 0 && now - firstTxTime > maxTxDelay) return true

        val transactionQueueSize = txQueue.getTransactionQueueSize()
        if (transactionQueueSize >= maxBlockTransactions) return true
        if (firstTxTime == 0L && transactionQueueSize > 0) {
            firstTxTime = now
            return false
        }

        return false
    }

    fun getBackoffTime(): Long = min(2.0.pow(failedBlockCount).toLong() + minBackoffTime, maxBackoffTime)
}