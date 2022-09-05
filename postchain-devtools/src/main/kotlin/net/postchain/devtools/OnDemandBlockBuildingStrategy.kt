// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import net.postchain.base.AbstractBlockBuilder
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import java.util.concurrent.LinkedBlockingQueue

/**
 * This block building strategy is very useful for tests.
 *
 * Not only can we tell the blockchain when to build blocks [buildBlocksUpTo()], we can also wait for a block
 * to get done [awaitCommitted()].
 */
@Suppress("UNUSED_PARAMETER")
class OnDemandBlockBuildingStrategy(
        val configData: BaseBlockBuildingStrategyConfigurationData,
        val blockQueries: BlockQueries,
        val txQueue: TransactionQueue
) : BlockBuildingStrategy {

    companion object : KLogging()

    @Volatile
    var upToHeight: Long = -1

    @Volatile
    var committedHeight = blockQueries.getBestHeight().get().toInt()
    val blocks = LinkedBlockingQueue<BlockData>()

    override fun shouldBuildBlock(): Boolean {
        return upToHeight > committedHeight
    }

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

    fun awaitCommitted(height: Int) {
        if (logger.isTraceEnabled) {
            logger.trace("awaitCommitted() - start: height: $height, committedHeight: $committedHeight")
        }
        while (committedHeight < height) {
            blocks.take()
            logger.debug { "awaitCommitted() - took a block height: $height, committedHeight: $committedHeight" }
        }
        var x = -2
        if (this.blockQueries != null) {
            x = blockQueries.getBestHeight().get().toInt()
        }
        if (logger.isTraceEnabled) {
            logger.trace("awaitCommitted() - end: height: $height, committedHeight: $committedHeight, from db: $x")
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        val abb = bb as AbstractBlockBuilder
        return abb.transactions.size >= configData.maxBlockTransactions
    }
}