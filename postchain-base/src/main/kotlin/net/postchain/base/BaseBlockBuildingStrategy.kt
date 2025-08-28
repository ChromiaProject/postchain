// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.concurrent.util.get
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.SpecialTxHandlerAware
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Suppress("SameReturnValue")
open class BaseBlockBuildingStrategy(val configData: BaseBlockBuildingStrategyConfigurationData,
                                     blockQueries: BlockQueries,
                                     private val txQueue: TransactionQueue,
                                     private val clock: Clock
) : BlockBuildingStrategy, SpecialTxHandlerAware {

    private var lastBlockTime: Long = max(0, blockQueries.getLastBlockTimestamp().get())
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

    private val forceStopBlockBuilding: AtomicBoolean = AtomicBoolean(false)

    private val maxFailCount = log2(maxBackoffTime.toDouble() - minBackoffTime).toLong()

    override var specialTxHandler: SpecialTransactionHandler? = null

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
        specialTxHandler?.blockCommitted(blockData)
    }

    override fun preemptiveBlockBuilding(): Boolean =
            preemptiveBlockBuilding && (specialTxHandler?.shouldAffectBlockBuilding() == false)

    override fun shouldBuildPreemptiveBlock(): Boolean =
            preemptiveBlockBuilding && (specialTxHandler?.shouldAffectBlockBuilding() == false)
                    && (txQueue.getTransactionQueueSize() > 0)

    override fun shouldBuildBlock(): Boolean {
        if (mustWaitMinimumBuildBlockTime() > 0) return false

        val transactionQueueSize = txQueue.getTransactionQueueSize()
        if (transactionQueueSize >= maxBlockTransactions) return true
        return if (hasReachedTimeConstraintsForBlockBuilding(transactionQueueSize > 0)) {
            true
        } else if (extendedShouldBuildBlock()) {
            true
        } else {
            specialTxHandler?.shouldBuildBlock() == true
        }
    }

    override fun shouldForceStopBlockBuilding(): Boolean = forceStopBlockBuilding.get()

    override fun setForceStopBlockBuilding(value: Boolean) {
        forceStopBlockBuilding.set(value)
    }

    override fun hasReachedTimeConstraintsForBlockBuilding(haveSeenTxs: Boolean): Boolean {
        val now = currentTimeMillis()

        if (now - lastBlockTime > maxBlockTime) return true
        if (firstTxTime > 0 && now - firstTxTime > maxTxDelay) return true

        if (firstTxTime == 0L && haveSeenTxs) {
            firstTxTime = now
        }

        return false
    }

    override fun mustWaitMinimumBuildBlockTime(): Long {
        val now = currentTimeMillis()
        return if (now - lastBlockTime < minInterBlockInterval) return minInterBlockInterval - (now - lastBlockTime) else 0
    }

    override fun mustWaitBeforeBuildBlock(): Boolean {
        val now = currentTimeMillis()
        return failedBlockTime > 0 && now - failedBlockTime < getBackoffTime()
    }

    private fun currentTimeMillis() = clock.millis()

    open fun extendedShouldBuildBlock(): Boolean = false

    fun getBackoffTime(): Long = if (failedBlockCount > maxFailCount) maxBackoffTime else
        min(2.0.pow(failedBlockCount).toLong() + minBackoffTime, maxBackoffTime)
}