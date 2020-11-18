package net.postchain.base

import net.postchain.core.BlockQueries
import net.postchain.core.SnapshotBuildingStrategy

class BaseSnapshotBuildingStrategy(
        val configData: BaseBlockchainConfigurationData,
        val blockQueries: BlockQueries
): SnapshotBuildingStrategy {

    private val strategyData = configData.getSnapshotBuildingStrategy()
    private val epoch = strategyData?.get("epoch")?.asInteger() ?: 1024

    override fun shouldBuildSnapshot(): Pair<Boolean, Long> {
        // build the snapshot for previous epoch
        val height = blockQueries.getBestHeight().get() - epoch
        if (height <= -1L) {
            return Pair(false, height)
        }
        return Pair((height+1) % epoch == 0L, height)
    }
}