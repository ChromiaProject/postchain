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
        val height = blockQueries.getBestHeight().get()
        if (height == -1L) {
            return false
        }
        return (height+1) % epoch == 0L
    }
}