package net.postchain.base

import net.postchain.core.BlockQueries
import net.postchain.core.SnapshotBuildingStrategy

class BaseSnapshotBuildingStrategy(
        val configData: BaseBlockchainConfigurationData,
        val blockQueries: BlockQueries
): SnapshotBuildingStrategy {

    private val strategyData = configData.getSnapshotBuildingStrategy()
    private val epoch = strategyData?.get("epoch")?.asInteger() ?: 1024

    override fun shouldBuildSnapshot(): Boolean {
        val height = blockQueries.getBestHeight().get() + 1
        return height % epoch == 0L
    }
}