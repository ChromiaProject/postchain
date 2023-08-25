// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import mu.KLogging
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import java.time.Clock
import java.util.concurrent.LinkedBlockingQueue

@Suppress("UNUSED_PARAMETER")
class ThreeTxStrategy(
        val configData: BaseBlockBuildingStrategyConfigurationData,
        blockQueries: BlockQueries,
        private val txQueue: TransactionQueue,
        val clock: Clock
) : BlockBuildingStrategy {

    companion object : KLogging()

    private val blocks = LinkedBlockingQueue<BlockData>()
    private var committedHeight = -1
    private val index = -1

    override fun preemptiveBlockBuilding(): Boolean = false

    override fun shouldBuildBlock(): Boolean {
        logger.debug { "PNode $index shouldBuildBlock? ${txQueue.getTransactionQueueSize()}" }
        return txQueue.getTransactionQueueSize() >= 3
    }

    override fun mustWaitMinimumBuildBlockTime(): Long = 0

    override fun mustWaitBeforeBuildBlock(): Boolean = false

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean = false

    override fun blockFailed() {}

    override fun blockCommitted(blockData: BlockData) {
        blocks.add(blockData)
        logger.debug { "PNode $index committed height ${blocks.size}" }
    }

    fun awaitCommitted(blockHeight: Int) {
        logger.debug { "PNode $index awaiting committed $blockHeight" }
        while (committedHeight < blockHeight) {
            blocks.take()
            committedHeight++
        }
    }
}