// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.concurrent.util.get
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries

class BaseBlockBuildingStrategy(val configData: BaseBlockBuildingStrategyConfigurationData,
                                blockQueries: BlockQueries,
                                private val txQueue: TransactionQueue
) : BlockBuildingStrategy {

    private var lastBlockTime: Long
    private var firstTxTime = 0L

    private val maxBlockTime = configData.maxBlockTime
    private val maxBlockTransactions = configData.maxBlockTransactions
    private val maxTxDelay = configData.maxTxDelay
    private val minInterBlockInterval = configData.minInterBlockInterval

    init {
        val height = blockQueries.getBestHeight().get()
        lastBlockTime = if (height == -1L) {
            0
        } else {
            val blockRID = blockQueries.getBlockRid(height).get()!!
            (blockQueries.getBlockHeader(blockRID).get() as BaseBlockHeader).timestamp
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        val abb = bb as AbstractBlockBuilder
        // TODO: fix end special transaction case
        return abb.transactions.size >= maxBlockTransactions
    }

    override fun blockCommitted(blockData: BlockData) {
        lastBlockTime = (blockData.header as BaseBlockHeader).timestamp
        firstTxTime = 0
    }

    override fun shouldBuildBlock(): Boolean {
        val now = System.currentTimeMillis()

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

}