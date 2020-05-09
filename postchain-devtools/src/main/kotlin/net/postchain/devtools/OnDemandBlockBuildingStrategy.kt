// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import mu.KLogging
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.core.*
import java.util.concurrent.LinkedBlockingQueue

@Suppress("UNUSED_PARAMETER")
class OnDemandBlockBuildingStrategy(
        configData: BaseBlockchainConfigurationData,
        val blockchainConfiguration: BlockchainConfiguration,
        blockQueries: BlockQueries,
        val txQueue: TransactionQueue
) : BlockBuildingStrategy {

    companion object : KLogging()

    @Volatile
    var upToHeight: Long = -1
    @Volatile
    var committedHeight = -1
    val blocks = LinkedBlockingQueue<BlockData>()
    private val strategyData = configData.getBlockBuildingStrategy()
    private val epoch = strategyData?.get("epoch")?.asInteger() ?: 1024

    override fun shouldBuildBlock(): Boolean {
        return upToHeight > committedHeight
    }

    override fun shouldBuildSnapshot(): Boolean {
        if (committedHeight == -1) {
            return false
        }
        return (committedHeight+1) % epoch == 0L
    }

    fun buildBlocksUpTo(height: Long) {
        upToHeight = height
    }

    override fun blockCommitted(blockData: BlockData) {
        committedHeight++
        blocks.add(blockData)
    }

    fun awaitCommitted(height: Int) {
        while (committedHeight < height) {
            blocks.take()
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        return false
    }
}