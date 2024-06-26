// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import net.postchain.base.AbstractBlockBuilder
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.concurrent.util.get
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import java.time.Clock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * This block building strategy is very useful for tests.
 *
 * Not only can we tell the blockchain when to build blocks [buildBlocksUpTo()], we can also wait for a block
 * to get done [awaitCommitted()].
 */
class OnDemandBlockBuildingStrategy(
        val configData: BaseBlockBuildingStrategyConfigurationData,
        val blockQueries: BlockQueries,
        val txQueue: TransactionQueue,
        val clock: Clock
) : BlockBuildingStrategy {

    companion object : KLogging()

    private val forceStopBlockBuilding: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    var upToHeight: Long = -1

    @Volatile
    var committedHeight = blockQueries.getLastBlockHeight().get().toInt()
    val blocks = LinkedBlockingQueue<BlockData>()

    override fun preemptiveBlockBuilding(): Boolean = false

    override fun shouldBuildPreemptiveBlock(): Boolean = false

    override fun shouldBuildBlock(): Boolean {
        return upToHeight > committedHeight
    }

    override fun shouldForceStopBlockBuilding(): Boolean = forceStopBlockBuilding.get()

    override fun setForceStopBlockBuilding(value: Boolean) {
        forceStopBlockBuilding.set(value)
    }

    override fun hasReachedTimeConstraintsForBlockBuilding(haveSeenTxs: Boolean): Boolean = false

    override fun mustWaitMinimumBuildBlockTime(): Long = 0

    override fun mustWaitBeforeBuildBlock(): Boolean = false

    fun buildBlocksUpTo(height: Long) {
        logger.trace { "buildBlocksUpTo() - height: $height" }
        upToHeight = height
    }

    override fun blockCommitted(blockData: BlockData) {
        committedHeight++
        if (logger.isTraceEnabled) {
            logger.trace("blockCommitted() - committedHeight: $committedHeight")
        }
        blocks.add(blockData)
    }

    /**
     *
     * @param timeout  time to wait for each block
     *
     * @throws TimeoutException if timeout
     */
    fun awaitCommitted(height: Int, timeout: Duration = Duration.INFINITE) {
        if (logger.isTraceEnabled) {
            logger.trace("awaitCommitted() - start: height: $height, committedHeight: $committedHeight")
        }
        while (committedHeight < height) {
            if (timeout.isInfinite()) {
                blocks.take()
            } else {
                blocks.poll(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                        ?: throw TimeoutException("Timeout waiting for block")
            }
            logger.debug { "awaitCommitted() - took a block height: $height, committedHeight: $committedHeight" }
        }
        val dbHeight = blockQueries.getLastBlockHeight().get().toInt()
        if (logger.isTraceEnabled) {
            logger.trace("awaitCommitted() - end: height: $height, committedHeight: $committedHeight, from db: $dbHeight")
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        val abb = bb as AbstractBlockBuilder
        return abb.transactions.size >= configData.maxBlockTransactions
    }

    override fun blockFailed() {}
}